package org.aion.avm.tooling.deploy.renamer;

import org.aion.avm.tooling.deploy.eliminator.ClassInfo;
import org.aion.avm.tooling.deploy.eliminator.MethodReachabilityDetector;
import org.aion.avm.utilities.JarBuilder;
import org.aion.avm.utilities.Utilities;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.ClassNode;

import java.io.*;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.JarInputStream;
import java.util.stream.Collectors;

public class Renamer {

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Input the path to the jar file.");
            System.exit(0);
        }

        try (FileInputStream fileInputStream = new FileInputStream(args[0])) {
            byte[] renamedJarBytes = rename(Utilities.stream_readAllBytes(fileInputStream));
            int pathLength = args[0].lastIndexOf("/") + 1;
            String outputJarName = args[0].substring(0, pathLength) + "renamed_" + args[0].substring(pathLength);
            writeOptimizedJar(outputJarName, renamedJarBytes);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(0);
        }
    }

    public static byte[] rename(byte[] jarBytes) throws Exception {
        JarInputStream jarReader = new JarInputStream(new ByteArrayInputStream(jarBytes), true);
        String mainClassName = Utilities.extractMainClassName(jarReader, Utilities.NameStyle.SLASH_NAME);
        Map<String, ClassNode> sortedClassMap = sortBasedOnInnerClassLevel(extractClasses(jarReader));

        Map<String, ClassNode> renamedNodes = renameClassNodes(sortedClassMap, mainClassName);

        Map<String, byte[]> classNameByteCodeMap = getClassBytes(renamedNodes);
        String newMainClassName = NameGenerator.getNewMainClassName();
        byte[] mainClassBytes = classNameByteCodeMap.get(newMainClassName);
        classNameByteCodeMap.remove(newMainClassName, mainClassBytes);

        return JarBuilder.buildJarForExplicitClassNamesAndBytecode(Utilities.internalNameToFulllyQualifiedName(newMainClassName), mainClassBytes, classNameByteCodeMap);
    }

    public static Map<String, ClassNode> sortBasedOnInnerClassLevel(Map<String, ClassNode> classMap) {
        Comparator<Map.Entry<String, ClassNode>> keyComparator =
                (n1, n2) -> Long.compare((n1.getKey().chars().filter(ch -> ch == '$').count()), (n2.getKey().chars().filter(ch -> ch == '$').count()));

        return classMap.entrySet().stream()
                .sorted(keyComparator)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
    }

    private static Map<String, ClassNode> renameClassNodes(Map<String, ClassNode> sortedClassMap, String mainClassName) throws Exception {
        //rename classes
        Map<String, String> mappedNames = ClassRenamer.renameClasses(sortedClassMap, mainClassName);
        Map<String, ClassNode> newClassNameMap = applyMapping(sortedClassMap, mappedNames);

        //rename methods
        String newMainClassName = mappedNames.get(mainClassName);
        Map<String, ClassInfo> classInfoMap = MethodReachabilityDetector.getClassInfoMap(newMainClassName, getClassBytes(newClassNameMap));
        mappedNames = MethodRenamer.renameMethods(newClassNameMap, classInfoMap);
        Map<String, ClassNode> newMethodNameMap = applyMapping(newClassNameMap, mappedNames);

        //rename fields
        mappedNames = FieldRenamer.renameFields(newMethodNameMap, classInfoMap);
        return applyMapping(newMethodNameMap, mappedNames);
    }

    private static Map<String, ClassNode> applyMapping(Map<String, ClassNode> classMap, Map<String, String> classNameMap) {
        SimpleRemapper remapper = new SimpleRemapper(classNameMap);
        Map<String, ClassNode> newClassMap = new HashMap<>();
        for (ClassNode node : classMap.values()) {
            ClassNode copy = new ClassNode();
            ClassRemapper adapter = new ClassRemapper(copy, remapper);
            node.accept(adapter);
            newClassMap.put(copy.name, copy);
        }
        return newClassMap;
    }

    public static Map<String, ClassNode> extractClasses(JarInputStream jarReader) throws IOException {
        Map<String, ClassNode> classMap = new HashMap<>();
        Map<String, byte[]> classByteMap = Utilities.extractClasses(jarReader, Utilities.NameStyle.SLASH_NAME);
        classByteMap.forEach((key, value) -> {
            ClassNode c = new ClassNode();
            new ClassReader(value).accept(c, 0);
            classMap.put(key, c);
        });
        return classMap;
    }

    private static Map<String, byte[]> getClassBytes(Map<String, ClassNode> classMap) {
        Map<String, byte[]> byteMap = new HashMap<>();
        for (ClassNode node : classMap.values()) {
            ClassWriter writer = new ClassWriter(0);
            node.accept(writer);
            byte[] classBytes = writer.toByteArray();
            byteMap.put(node.name, classBytes);
        }
        return byteMap;
    }

    private static void writeOptimizedJar(String jarName, byte[] jarBytes) {
        try {
            DataOutputStream dout = new DataOutputStream(new FileOutputStream(jarName));
            dout.write(jarBytes);
            dout.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("Successfully created jar. \n" + jarName);
    }
}
