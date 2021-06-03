package net.minecraftforge.mcpconfig.tasks

import org.gradle.api.*
import org.gradle.api.tasks.*
import de.undercouch.gradle.tasks.download.DownloadAction
import java.io.File
import java.util.HashMap
import java.util.Map

public class DownloadRuntime extends DefaultTask {
    @InputFile File json
    @Input String os
    
    @OutputFiles Map<String, File> dest = new HashMap<>()
    
    @TaskAction
    def exec() {
        Utils.init()
        logger.lifecycle('Runtimes: ')
        dest.forEach{ runtime_name,output -> 
            logger.lifecycle('  ' + runtime_name + ' -> ' + output)
            def data = json.json.get(os)?.get(runtime_name)
            if (data == null)
                throw new IllegalStateException('Could not find ' + os + '.' + runtime_name + ' in ' + json)
            
            def manifest = new File(output, 'manifest.json')
            if (!manifest.exists() || !manifest.sha1.equals(data.manifest.sha1)) {
                download(data.manifest.url, manifest)
            }
            
            manifest.json.files.forEach{ file_name,info ->
                if (info.type == 'directory')
                    return
                if (info.type != 'file')
                    throw new IllegalArgumentException('Unknown type: ' + info.type + ' for ' + file_name + ' in ' + manifest)
                def target = new File(output, 'files/' + file_name)
                
                //TODO: Eventually support lzma variants to save bandwidth? IDGAF right now.
                if (target.exists() && target.sha1.equals(info.downloads.raw.sha1))
                    return
                
                download(info.downloads.raw.url, target)
            }
        }
    }

    def download(def url, def target) {
        def ret = new DownloadAction(project, this)
        ret.overwrite(false)
        ret.useETag('all')
        ret.src url
        ret.dest target
        ret.execute()
    }
}