package net.minecraftforge.mcpconfig.tasks;

import java.util.*

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
}