package net.minecraftforge.mcpconfig.tasks;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

public class ConvertMappings extends DefaultTask {

    private File proguard;
	
	public void proguard(Object o) {
		this.proguard = (File) o;
	}

    private File tsrg;
	
	public void tsrg(Object o) {
		this.tsrg = (File) o;
	}
	
	private boolean overwrite = false;
	
	public void overwrite(Object o) {
		this.overwrite = (Boolean) o;
	}

    private Map<String, String> classMap = new HashMap<>();

    @TaskAction
    public void doTask() throws IOException {
        loadClasses();
		if(!tsrg.exists() || overwrite)
			tsrg.createNewFile();
        writeTsrg();
    }

    // can only be called after #loadClasses or class parameters will not get obfuscated
    private String typeToDescriptor(String type) {
        if(type.endsWith("[]")) // array type
            return "[" + typeToDescriptor(type.substring(0, type.length() - 2));
        if(type.contains("."))  // class type
            return "L" + classMap.getOrDefault(type, type).replaceAll("\\.", "/") + ";";

        switch(type) { // primitive type
            case    "byte": return "B";
            case    "char": return "C";
            case  "double": return "D";
            case   "float": return "F";
            case     "int": return "I";
            case    "long": return "J";
            case   "short": return "S";
            case    "void": return "V";
            case "boolean": return "Z";
            default: return "";
        }
    }

    private void loadClasses() throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(proguard));

        while(true) {
            String line = reader.readLine();
            if (line == null || line.isEmpty()) break; // end of file

            if (line.startsWith("#")) continue; // comment line

            if (line.startsWith(" ")) continue; // field or method line

            String parts[] = line.split(" ");
            assert parts.length == 3; // [<class name>],[->],[<obfuscated name>:]

            String className = parts[0];
            String obfName   = parts[2].substring(0, parts[2].length() - 1); // strip colon

            classMap.put(className, obfName);
        }

        reader.close();
    }

    private void writeTsrg() throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(proguard));
        BufferedWriter writer = new BufferedWriter(new FileWriter(tsrg));

        while(true) {
            String line = reader.readLine();
            if (line == null || line.isEmpty()) break; // end of file

            if (line.startsWith("#")) continue; // comment line

            if (line.startsWith(" ")) { // field or method line
                line = line.substring(4); // remove leading spaces

                String parts[] = line.split(" ");
                assert parts.length == 4; // [<type>],[<name>],[->],[<obfuscated name>]

                if(parts[1].endsWith(")")) { // method line
                    String returnType = parts[0].contains(":")
                        ? parts[0].split(":")[2] // [<#>],[<#>],[<type>]
                        : parts[0];

                    String obfName    = parts[3];
                    String methodName = parts[1].split("\\(")[0]; // [<name>],[<params...>)]
                    String params     = parts[1].split("\\(")[1];
                    params = params.substring(0, params.length() - 1); // strip ) from params

                    returnType = typeToDescriptor(returnType);
                    params = Arrays.stream(params.split(",")) // [<param>],[<param>], ...
                        .map(this::typeToDescriptor)
                        .collect(Collectors.joining());

                    if(methodName.equals("<init>") || methodName.equals("<clinit>")) continue;

                    writer.write(String.format("\t%s (%s)%s %s\n", obfName, params, returnType, methodName));
                } else { // field line
                    String fieldName = parts[1];
                    String obfName   = parts[3];

                    writer.write("\t" + obfName + " " + fieldName + "\n");
                }
            } else { // class line
                String parts[] = line.split(" ");
                assert parts.length == 3; // [<class name>],[->],[<obfuscated name>:]

                String className = parts[0];
                String obfName   = parts[2].substring(0, parts[2].length() - 1); // strip colon

                writer.write(new String(obfName + " " + className + "\n").replaceAll("\\.","/"));
            }
        }

        writer.close();
        reader.close();
    }

}
