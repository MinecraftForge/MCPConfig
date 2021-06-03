package net.minecraftforge.mcpconfig.tasks;

import java.util.*
import java.util.regex.Matcher
import java.util.regex.Pattern

import java.security.MessageDigest

import groovy.json.JsonBuilder
import groovy.json.JsonSlurper

class Utils {
    public static init() {
        String.metaClass.rsplit = { chr -> [delegate.substring(0, delegate.lastIndexOf(chr)), delegate.substring(delegate.lastIndexOf(chr)+1)] }
        String.metaClass.toMavenPath = {
            if (delegate == null) return null
            def tmp = delegate
            def ext = 'jar'
            def idx = tmp.indexOf('@')
            if (idx != -1) {
                ext = tmp.substring(idx+1)
                tmp = tmp.substring(0,idx)
            }
            def pts = tmp.split(':')
            return pts[0].replace('.', '/') + '/' + pts[1] + '/' + pts[2] + '/' + pts[1] + '-' + pts[2] + (pts.length > 3 ? '-' + pts[3] : '') + '.' + ext
        }
        File.metaClass.getSha1 = { !delegate.exists() ? null : MessageDigest.getInstance('SHA-1').digest(delegate.bytes).encodeHex().toString() }
        
        File.metaClass.getJson = { return delegate.exists() ? new JsonSlurper().parse(delegate) : [:] }
        File.metaClass.setJson = { json -> delegate.text = new JsonBuilder(json).toPrettyString() }
    }
    
    static def fillVariables(List<String> args, Map<String, Object> props) {
        def ret = []
        args.each{ arg -> 
            if (!arg.startsWith('{') || !arg.endsWith('}') || !props.containsKey(arg.substring(1, arg.length() -1))) {
                ret.add(arg)
            } else {
                def val = props.get(arg.substring(1, arg.length() - 1))
                ret.add(val instanceof File ? val.absolutePath : val.toString())
            }
        }
        return ret
    }
    
    static def testJsonRules(rules) {
        if (rules == null) return true
        def allow = false
        for (def rule : rules) {
            def matched = true
            if (rule.os != null) {
                if (rule.os.name != null)
                    matched &= getOsName().equals(rule.os.name)
                if (rule.os.version != null)
                    matched &= Pattern.compile(rule.os.version).matcher(System.getProperty('os.version')).find()
                if (rule.os.arch != null)
                    matched &= Pattern.compile(rule.os.arch).matcher(System.getProperty('os.arch')).find()
            }
            if (matched) allow = 'allow'.equals(rule.action)
        }
        return allow
    }
    
    static def getOsName() {
        def name = System.getProperty('os.name').toLowerCase(java.util.Locale.ENGLISH)
        if (name.contains('windows') || name.contains('win')) return 'windows'
        if (name.contains('linux') || name.contains('unix')) return 'linux'
        if (name.contains('osx') || name.contains('mac')) return 'osx'
        return 'unknown'
    }
    
    static def readConfig(def cfg, def name, def defaults) {
        def ent = cfg.get(name)
        def version = ent?.version ?: defaults.version ?: null
        
        return [
            version: version,
            args: ent?.args ?: defaults.args ?: [],
            jvmargs: ent?.jvmargs ?: defaults.jvmargs ?: [],
            path: version?.toMavenPath(),
            repo: ent?.repo ?: defaults.repo ?: 'https://maven.minecraftforge.net/'
        ]
    }
}