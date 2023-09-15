package net.minecraftforge.mcpconfig.tasks

import org.gradle.api.*
import org.gradle.api.tasks.*
import de.undercouch.gradle.tasks.download.Download

public class DownloadTool extends Download {
    @Input config
    
    public DownloadTool() {
        useETag 'all'
        onlyIfModified true
        quiet true
    }
    
    def config(def cfg, String root) {
        this.config = cfg
        src cfg.repo + cfg.path
        dest new File(root + cfg.path)
    }
}