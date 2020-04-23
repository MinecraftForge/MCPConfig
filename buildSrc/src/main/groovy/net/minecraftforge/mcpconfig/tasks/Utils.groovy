package net.minecraftforge.mcpconfig.tasks;

import java.util.*
import java.util.regex.Matcher
import java.util.regex.Pattern

class Utils {
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
}