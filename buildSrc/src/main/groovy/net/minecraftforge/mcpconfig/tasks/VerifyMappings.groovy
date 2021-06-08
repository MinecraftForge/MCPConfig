package net.minecraftforge.mcpconfig.tasks

import net.minecraftforge.mappingverifier.IVerifier
import net.minecraftforge.mappingverifier.SimpleVerifier
import net.minecraftforge.srgutils.INamedMappingFile
import org.gradle.api.*
import org.gradle.api.tasks.*
import org.gradle.internal.MutableBoolean

import java.util.function.Predicate

import net.minecraftforge.mappingverifier.MappingVerifier

public class VerifyMappings extends DefaultTask {
    @InputFile mappings
    @InputFile joined
    @InputFile o2s2idMappings
    
    @TaskAction
    def exec() {
        Utils.init()

        MappingVerifier mv = new MappingVerifier()
        mv.loadMap(mappings)
        mv.loadJar(joined)
        mv.addDefaultTasks()

        def die = false
        if (!mv.verify()) {
            for (def t : mv.tasks) {
                if (!t.errors.isEmpty()) {
                    logger.lifecycle('Task: ' + t.name)
                    t.errors.each{ logger.lifecycle('  ' + it.stripIndent()) }
                    die = true
                }
            }
        }

        mv.getTasks().clear()
        mv.setMap(INamedMappingFile.load(o2s2idMappings).getMap('srg', 'id'))
        IVerifier intIdVerifier = new UniqueIntIdVerifier(mv)
        mv.addTask(intIdVerifier)
        if (!mv.verify()) {
            logger.lifecycle('Task: UniqueIntIdVerifier')
            intIdVerifier.errors.each{ logger.lifecycle('  ' + it.stripIndent()) }
            die = true
        }

        if (die)
            throw new RuntimeException('Verification failed')
    }

    static class NameFormatVerifier extends SimpleVerifier {
        private final Predicate<String> nameTester
        private final List<String> validIds = new ArrayList<>()

        protected NameFormatVerifier(MappingVerifier verifier, Predicate<String> nameTester) {
            super(verifier)
            this.nameTester = nameTester
        }

        @Override
        boolean process() {
            MutableBoolean valid = new MutableBoolean(true)

            verifier.getMappings().getClasses().each {cls ->
                check(cls.getMapped(), 'Class', valid)

                cls.getFields().each {fld -> check(fld.getMapped(), 'Field', valid)}

                cls.getMethods().forEach{mtd ->
                    check(mtd.getMapped(), 'Method', valid)

                    mtd.getParameters().each {param -> check(param.getMapped(), 'Param', valid)}
                }
            }
            return valid.get()
        }

        void check(String id, String type, MutableBoolean valid) {
            if (!nameTester.test(id)) {
                error(type + ' id: ' + id + ' has incorrect format')
                valid.set(false)
            }
            validIds.add(id)
        }

        List<String> getValidIds() {
            return validIds
        }
    }

    static class UniqueIntIdVerifier extends NameFormatVerifier {
        private static final Predicate<String> INT_ID_VERIFIER = { String s ->
            try {
                Integer.parseInt(s)
            } catch(NumberFormatException ignored) {
                return false
            }
            return true
        }

        protected UniqueIntIdVerifier(MappingVerifier verifier) {
            super(verifier, INT_ID_VERIFIER)
        }

        @Override
        boolean process() {
            boolean superProcess = super.process()

            Set<String> unique = new HashSet<>()
            Set<String> duplicates = new HashSet<>()

            super.getValidIds().each {id ->
                if (unique.contains(id))
                    duplicates.add(id)
                else
                    unique.add(id)
            }

            duplicates.each {s -> error('Id ' + s + ' is a duplicate.')}

            return superProcess && duplicates.isEmpty()
        }
    }
}