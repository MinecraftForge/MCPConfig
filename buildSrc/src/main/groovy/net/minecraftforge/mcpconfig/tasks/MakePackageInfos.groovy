package net.minecraftforge.mcpconfig.tasks

import org.gradle.api.*
import org.gradle.api.tasks.*
import java.util.zip.*
import java.nio.charset.StandardCharsets
import java.nio.file.attribute.FileTime

public class MakePackageInfos extends SingleFileOutput {
    @InputFile input
    @InputFile @Optional template
    
    @TaskAction
    def exec() {
        Utils.init()
        new ZipInputStream(input.newInputStream()).withCloseable{ jin ->
            new ZipOutputStream(dest.newOutputStream()).withCloseable{ jout ->
                def packages = [] as HashSet

                for (def entry = jin.nextEntry; entry != null; entry = jin.nextEntry) {
                    def name = entry.name
                    def folder = entry.isDirectory() && !entry.name.endsWith('/') ? name : 
                                 entry.name.indexOf('/') == -1 ? '' : entry.name.substring(0, entry.name.lastIndexOf('/'))
                    packages.add(folder)
                }
                
                if (template != null) {
                    packages.removeAll(['', 'net', 'net/minecraft', 'com', 'com/mojang'])
                    def templateD = template.text
                    def timestamp = FileTime.fromMillis(1000)
                    packages.each{ pkg ->
                        def oentry = new ZipEntry(pkg + '/package-info.java')
                        oentry.lastModifiedTime = timestamp
                        jout.putNextEntry(oentry)
                        jout.write(templateD.replace('{PACKAGE}', pkg.replaceAll('/', '.')).getBytes(StandardCharsets.UTF_8))
                    }
                }
            }
        }
    }
}