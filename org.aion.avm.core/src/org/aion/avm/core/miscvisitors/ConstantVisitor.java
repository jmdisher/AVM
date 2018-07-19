package org.aion.avm.core.miscvisitors;

import java.util.HashMap;
import java.util.Map;

import org.aion.avm.core.ClassToolchain;
import org.aion.avm.internal.PackageConstants;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodNode;


/**
 * A dedicated visitor to deal with String and Class constants.
 *
 * A class visitor which replaces the "ConstantValue" static final String constants with a "ldc+putstatic" pair in the &lt;clinit&gt;
 * (much like Class constants).
 * This should be one of the earliest visitors in the chain since the transformation produces code with no special assumptions,
 * the re-write needs to happen before shadowing and metering, and it depends on nothing other than this being valid bytecode.
 * Note that ASM defines that visitField is called before visitMethod so we can collect the constants we need to re-write before
 * we will see the method we need to modify.
 * If the &lt;clinit&gt; exists, we will prepend this ldc+pustatic pairs.  If it doesn't, we will generate it last.
 */
public class ConstantVisitor extends ClassToolchain.ToolChainClassVisitor {
    private static final String kClinitName = "<clinit>";
    private static final int kClinitAccess = Opcodes.ACC_STATIC;
    private static final String kClinitDescriptor = "()V";

    private static final String postRenameStringDescriptor = "L" + PackageConstants.kShadowSlashPrefix + "java/lang/String;";
    private static final String wrapStringMethodName = "wrapAsString";
    private static final String wrapStringMethodDescriptor = "(Ljava/lang/String;)L" + PackageConstants.kShadowSlashPrefix + "java/lang/String;";

    private static final String wrapClassMethodName = "wrapAsClass";
    private static final String wrapClassMethodDescriptor = "(Ljava/lang/Class;)L" + PackageConstants.kShadowSlashPrefix + "java/lang/Class;";

    private final Map<String, String> staticFieldNamesToConstantValues;
    private final String runtimeClassName;
    private String thisClassName;
    private MethodNode cachedClinit;

    public ConstantVisitor(String runtimeClassName) {
        super(Opcodes.ASM6);
        this.runtimeClassName = runtimeClassName;
        this.staticFieldNamesToConstantValues = new HashMap<>();
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        // We just need this to capture the name for the "owner" in the PUTSTATIC calls.
        this.thisClassName = name;
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
        // The special case we want to handle is a field with the following properties:
        // -static
        // -non-null value
        // -String
        Object filteredValue = value;
        if ((0 != (access & Opcodes.ACC_STATIC))
                && (null != value)
                && postRenameStringDescriptor.equals(descriptor)
        ) {
            // We need to do something special in this case.
            this.staticFieldNamesToConstantValues.put(name, (String)value);
            filteredValue = null;
        }
        return super.visitField(access, name, descriptor, signature, filteredValue);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        // If this is a clinit, capture it into the MethodNode, for later use.  Otherwise, pass it on as normal.
        MethodVisitor visitor = null;
        if (kClinitName.equals(name)) {
            this.cachedClinit = new MethodNode(access, name, descriptor, signature, exceptions);
            visitor = this.cachedClinit;
        } else {
            visitor = super.visitMethod(access, name, descriptor, signature, exceptions);
        }

        return new MethodVisitor(Opcodes.ASM6, visitor) {
            @Override
            public void visitLdcInsn(Object value) {
                if (value instanceof Type && ((Type) value).getSort() == Type.OBJECT) {
                    // class constants
                    // TODO: should we load the original class or the shadow class?
                    super.visitLdcInsn(value);
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, runtimeClassName, wrapClassMethodName, wrapClassMethodDescriptor, false);
                } else if (value instanceof String) {
                    // string constants
                    super.visitLdcInsn(value);
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, runtimeClassName, wrapStringMethodName, wrapStringMethodDescriptor, false);
                } else {
                    // numbers?
                    super.visitLdcInsn(value);
                }
            }
        };
    }

    @Override
    public void visitEnd() {
        // Note that visitEnd happens immediately after visitMethod, so we can synthesize the <clinit> here, if it is needed.
        // We want to write the <clinit> if either there was one (which we cached) or we have constant values to dump into it.
        if ((null != this.cachedClinit) || !this.staticFieldNamesToConstantValues.isEmpty()) {
            // Create the actual visitor for the clinit.
            MethodVisitor clinitVisitor = super.visitMethod(kClinitAccess, kClinitName, kClinitDescriptor, null, null);
            
            // Prepend the ldc+putstatic pairs.
            for (Map.Entry<String, String> elt : this.staticFieldNamesToConstantValues.entrySet()) {
                // load constant
                clinitVisitor.visitLdcInsn(elt.getValue());

                // wrap as shadow string
                clinitVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, runtimeClassName, wrapStringMethodName, wrapStringMethodDescriptor, false);

                // set the field
                clinitVisitor.visitFieldInsn(Opcodes.PUTSTATIC, this.thisClassName, elt.getKey(), postRenameStringDescriptor);
            }
            this.staticFieldNamesToConstantValues.clear(); 
            
            // Dump the remaining <clinit> into the visitor or synthesize the end of it, if we don't have a cached one.
            if (null != this.cachedClinit) {
                this.cachedClinit.accept(clinitVisitor);
            } else {
                clinitVisitor.visitInsn(Opcodes.RETURN);
                clinitVisitor.visitMaxs(1, 0);
                clinitVisitor.visitEnd();
            }
        }
        super.visitEnd();
    }
}
