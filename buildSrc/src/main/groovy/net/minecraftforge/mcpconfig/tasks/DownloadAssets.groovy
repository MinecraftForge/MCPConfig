package net.minecraftforge.mcpconfig.tasks

import org.gradle.api.*
import org.gradle.api.tasks.*
import de.undercouch.gradle.tasks.download.DownloadAction

public class DownloadAssets extends DefaultTask {
    @InputFile json
    @OutputDirectory def dest
    
    @TaskAction
    def exec() {
        Utils.init()
        
        def dl = json.json.assetIndex
        def index = new File(dest, 'indexes/' + dl.id + '.json')
        if (index.sha1 != dl.sha1)
            download(dl.url, index)
        
        index.json.objects.each { asset ->
            def key = asset.value.hash.take(2) + '/' + asset.value.hash
            def target = new File(dest, 'objects/' + key)
            if (!target.exists())
                download('https://resources.download.minecraft.net/' + key, target)
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