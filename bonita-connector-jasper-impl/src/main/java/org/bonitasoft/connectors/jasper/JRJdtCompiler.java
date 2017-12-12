package org.bonitasoft.connectors.jasper;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import net.sf.jasperreports.engine.design.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRPropertiesUtil;
import net.sf.jasperreports.engine.JRReport;
import net.sf.jasperreports.engine.JRRuntimeException;
import net.sf.jasperreports.engine.JasperReportsContext;
import net.sf.jasperreports.engine.util.JRClassLoader;
import net.sf.jasperreports.engine.util.JRLoader;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.internal.compiler.*;
import org.eclipse.jdt.internal.compiler.Compiler;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileReader;
import org.eclipse.jdt.internal.compiler.env.IBinaryType;
import org.eclipse.jdt.internal.compiler.env.ICompilationUnit;
import org.eclipse.jdt.internal.compiler.env.INameEnvironment;
import org.eclipse.jdt.internal.compiler.env.NameEnvironmentAnswer;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;
import org.eclipse.jdt.internal.compiler.problem.DefaultProblemFactory;

public class JRJdtCompiler extends JRAbstractJavaCompiler {
    private static final String JDT_PROPERTIES_PREFIX = "org.eclipse.jdt.core.";

    private static final Log log = LogFactory.getLog(JRJdtCompiler.class);

    private final ClassLoader classLoader;

    private Constructor<?> constrNameEnvAnsBin;
    private Constructor<?> constrNameEnvAnsCompUnit;

    private boolean is2ArgsConstr;
    private Constructor<?> constrNameEnvAnsBin2Args;
    private Constructor<?> constrNameEnvAnsCompUnit2Args;

    public static JasperReportsContext jasperReportsContext;

    /**
     *
     */
    public JRJdtCompiler() {
        super(jasperReportsContext, false);
        classLoader = getClassLoader();
        try {
            Class<?> classAccessRestriction = NameEnvironmentAnswer.class.getClassLoader().loadClass("org.eclipse.jdt.internal.compiler.env.AccessRestriction");
            constrNameEnvAnsBin2Args = NameEnvironmentAnswer.class.getConstructor(IBinaryType.class, classAccessRestriction);
            constrNameEnvAnsCompUnit2Args = NameEnvironmentAnswer.class.getConstructor(ICompilationUnit.class, classAccessRestriction);
            is2ArgsConstr = true;
        } catch (NoSuchMethodException | ClassNotFoundException e) {
            try {
                constrNameEnvAnsBin = NameEnvironmentAnswer.class.getConstructor(IBinaryType.class);
                constrNameEnvAnsCompUnit = NameEnvironmentAnswer.class.getConstructor(ICompilationUnit.class);
                is2ArgsConstr = false;
            } catch (NoSuchMethodException ex) {
                throw new JRRuntimeException("Error loading the compiler", ex);
            }
        }
    }

    @Override
    protected String compileUnits(final JRCompilationUnit[] units, String classpath, File tempDirFile) {
        final INameEnvironment env = getNameEnvironment(units);
        final IErrorHandlingPolicy policy = DefaultErrorHandlingPolicies.proceedWithAllProblems();
        final Map<String, String> settings = getJdtSettings();
        final IProblemFactory problemFactory = new DefaultProblemFactory(Locale.getDefault());
        final CompilerRequestor requestor = getCompilerRequestor(units);
        final Compiler compiler = new Compiler(env, policy, settings, requestor, problemFactory);
        CompilationUnit[] compilationUnits = requestor.processCompilationUnits();
        compiler.compile(compilationUnits);
        return requestor.getFormattedProblems();
    }

    private INameEnvironment getNameEnvironment(final JRCompilationUnit[] units) {
        return new INameEnvironment() {
            @Override
            public NameEnvironmentAnswer findType(char[][] compoundTypeName) {
                StringBuilder result = new StringBuilder();
                String sep = "";
                for (char[] aCompoundTypeName : compoundTypeName) {
                    result.append(sep);
                    result.append(aCompoundTypeName);
                    sep = ".";
                }
                return findType(result.toString());
            }

            @Override
            public NameEnvironmentAnswer findType(char[] typeName, char[][] packageName) {
                StringBuilder result = new StringBuilder();
                String sep = "";
                for (char[] aPackageName : packageName) {
                    result.append(sep);
                    result.append(aPackageName);
                    sep = ".";
                }
                result.append(sep);
                result.append(typeName);
                return findType(result.toString());
            }

            private int getClassIndex(String className) {
                int classIdx;
                for (classIdx = 0; classIdx < units.length; ++classIdx) {
                    if (className.equals(units[classIdx].getName())) {
                        break;
                    }
                }
                if (classIdx >= units.length) {
                    classIdx = -1;
                }
                return classIdx;
            }

            private NameEnvironmentAnswer findType(String className) {
                try {
                    int classIdx = getClassIndex(className);
                    if (classIdx >= 0) {
                        ICompilationUnit compilationUnit =
                                new CompilationUnit(
                                        units[classIdx].getSourceCode(), className);
                        if (is2ArgsConstr) {
                            return (NameEnvironmentAnswer) constrNameEnvAnsCompUnit2Args.newInstance(new Object[]{compilationUnit, null});
                        }
                        return (NameEnvironmentAnswer) constrNameEnvAnsCompUnit.newInstance(new Object[]{compilationUnit});
                    }
                    String resourceName = className.replace('.', '/') + ".class";
                    try (InputStream is = getResource(resourceName)) {
                        if (is != null) {
                            byte[] classBytes = JRLoader.loadBytes(is);
                            char[] fileName = className.toCharArray();
                            ClassFileReader classFileReader = new ClassFileReader(classBytes, fileName, true);

                            if (is2ArgsConstr) {
                                return (NameEnvironmentAnswer) constrNameEnvAnsBin2Args.newInstance(new Object[]{classFileReader, null});
                            }
                            return (NameEnvironmentAnswer) constrNameEnvAnsBin.newInstance(new Object[]{classFileReader});
                        }
                    }
                } catch (IOException | JRException | org.eclipse.jdt.internal.compiler.classfmt.ClassFormatException e) {
                    log.error("Compilation error", e);
                } catch (InvocationTargetException | IllegalAccessException | InstantiationException | IllegalArgumentException e) {
                    throw new JRRuntimeException("Bad environment", e);
                }
                return null;
            }

            private boolean isPackage(String result) {
                int classIdx = getClassIndex(result);
                if (classIdx >= 0) {
                    return false;
                }
                boolean isPackage = true;
                try (InputStream is = getResource(result.replace('.', '/') + ".class")) {
                    if (is != null) {
                        isPackage = (is.read() < 0);
                    }
                } catch (IOException ignored) {
                }
                return isPackage;
            }

            @Override
            public boolean isPackage(char[][] parentPackageName, char[] packageName) {
                StringBuilder result = new StringBuilder();
                String sep = "";
                if (parentPackageName != null) {
                    for (char[] aParentPackageName : parentPackageName) {
                        result.append(sep);
                        result.append(aParentPackageName);
                        sep = ".";
                    }
                }
                if (Character.isUpperCase(packageName[0])) {
                    if (!isPackage(result.toString())) {
                        return false;
                    }
                }
                result.append(sep);
                result.append(packageName);
                return isPackage(result.toString());
            }

            @Override
            public void cleanup() {
            }
        };
    }

    private CompilerRequestor getCompilerRequestor(final JRCompilationUnit[] units) {
        return new CompilerRequestor(this, units);
    }

    private Map<String, String> getJdtSettings() {
        final Map<String, String> settings = new HashMap<>();
        settings.put(CompilerOptions.OPTION_LineNumberAttribute, CompilerOptions.GENERATE);
        settings.put(CompilerOptions.OPTION_SourceFileAttribute, CompilerOptions.GENERATE);
        settings.put(CompilerOptions.OPTION_ReportDeprecation, CompilerOptions.IGNORE);
        List<JRPropertiesUtil.PropertySuffix> properties = JRPropertiesUtil.getInstance(jasperReportsContext).getProperties(JDT_PROPERTIES_PREFIX);
        for (JRPropertiesUtil.PropertySuffix property : properties) {
            String propVal = property.getValue();
            if (propVal != null && propVal.length() > 0) {
                settings.put(property.getKey(), propVal);
            }
        }
        Properties systemProps = System.getProperties();
        for (String propName : systemProps.stringPropertyNames()) {
            if (propName.startsWith(JDT_PROPERTIES_PREFIX)) {
                String propVal = systemProps.getProperty(propName);
                if (propVal != null && propVal.length() > 0) {
                    settings.put(propName, propVal);
                }
            }
        }

        return settings;
    }

    private ClassLoader getClassLoader() {
        ClassLoader clsLoader = Thread.currentThread().getContextClassLoader();

        if (clsLoader != null) {
            try {
                Class.forName(JRJdtCompiler.class.getName(), true, clsLoader);
            } catch (ClassNotFoundException e) {
                clsLoader = null;
            }
        }
        if (clsLoader == null) {
            clsLoader = JRClassLoader.class.getClassLoader();
        }

        return clsLoader;
    }

    private InputStream getResource(String resourceName) {
        if (classLoader == null) {
            return JRJdtCompiler.class.getResourceAsStream("/" + resourceName);
        }
        return classLoader.getResourceAsStream(resourceName);
    }


    @Override
    protected void checkLanguage(String language) throws JRException {
        if (!JRReport.LANGUAGE_JAVA.equals(language)) {
            throw new JRException("expected.java");
        }
    }


    @Override
    protected JRCompilationSourceCode generateSourceCode(JRSourceCompileTask sourceTask) throws JRException {
        return JRClassGenerator.generateClass(sourceTask);
    }


    @Override
    protected String getSourceFileName(String unitName) {
        return unitName + ".java";
    }


    @Override
    protected String getCompilerClass() {
        return JRJavacCompiler.class.getName();
    }


    public static class CompilerRequestor implements ICompilerRequestor {

        final JRJdtCompiler compiler;
        final JRCompilationUnit[] units;
        final CompilationUnitResult[] unitResults;

        CompilerRequestor(final JRJdtCompiler compiler, final JRCompilationUnit[] units) {
            this.compiler = compiler;
            this.units = units;
            this.unitResults = new CompilationUnitResult[units.length];
            reset();
        }

        @Override
        public void acceptResult(CompilationResult result) {
            String className = ((CompilationUnit) result.getCompilationUnit()).className;
            int classIdx;
            for (classIdx = 0; classIdx < units.length; ++classIdx) {
                if (className.equals(units[classIdx].getName())) {
                    break;
                }
            }
            if (result.hasErrors()) {
                unitResults[classIdx].problems = getJavaCompilationErrors(result);
            } else {
                ClassFile[] resultClassFiles = result.getClassFiles();
                for (ClassFile resultClassFile : resultClassFiles) {
                    units[classIdx].setCompileData(resultClassFile.getBytes());
                }
            }
        }


        String getFormattedProblems() {
            StringBuilder problemBuilder = new StringBuilder();
            for (int u = 0; u < units.length; u++) {
                String sourceCode = units[u].getSourceCode();
                IProblem[] problems = unitResults[u].problems;
                if (problems != null && problems.length > 0) {
                    for (int i = 0; i < problems.length; i++) {
                        IProblem problem = problems[i];
                        problemBuilder.append(i + 1);
                        problemBuilder.append(". ");
                        problemBuilder.append(problem.getMessage());
                        if (problem.getSourceStart() >= 0 && problem.getSourceEnd() >= 0) {
                            int problemStartIndex = sourceCode.lastIndexOf("\n", problem.getSourceStart()) + 1;
                            int problemEndIndex = sourceCode.indexOf("\n", problem.getSourceEnd());
                            if (problemEndIndex < 0) {
                                problemEndIndex = sourceCode.length();
                            }
                            problemBuilder.append("\n");
                            problemBuilder.append(sourceCode.substring(problemStartIndex, problemEndIndex));
                            problemBuilder.append("\n");
                            for (int j = problemStartIndex; j < problem.getSourceStart(); j++) {
                                problemBuilder.append(" ");
                            }
                            if (problem.getSourceStart() == problem.getSourceEnd()) {
                                problemBuilder.append("^");
                            } else {
                                problemBuilder.append("<");
                                for (int j = problem.getSourceStart() + 1; j < problem.getSourceEnd(); j++) {
                                    problemBuilder.append("-");
                                }
                                problemBuilder.append(">");
                            }
                            problemBuilder.append("\n");
                        }
                    }
                    problemBuilder.append(problems.length);
                    problemBuilder.append(" errors\n");
                }
            }
            return problemBuilder.length() > 0 ? problemBuilder.toString() : null;
        }

        CompilationUnit[] processCompilationUnits() {
            final CompilationUnit[] compilationUnits = new CompilationUnit[units.length];

            for (int i = 0; i < compilationUnits.length; i++) {
                compilationUnits[i] = new CompilationUnit(units[i].getSourceCode(), units[i].getName());
            }
            reset();
            return compilationUnits;
        }

        void reset() {
            for (int i = 0; i < unitResults.length; i++) {
                if (unitResults[i] == null) {
                    unitResults[i] = new CompilationUnitResult();
                }
                unitResults[i].reset();
            }
        }

        IProblem[] getJavaCompilationErrors(CompilationResult result) {
            try {
                Method getErrorsMethod = result.getClass().getMethod("getErrors", (Class[]) null);
                return (IProblem[]) getErrorsMethod.invoke(result, (Object[]) null);
            } catch (SecurityException | NoSuchMethodException | IllegalArgumentException | IllegalAccessException | InvocationTargetException e) {
                throw new JRRuntimeException("Compilation error", e);
            }
        }
    }

    private static class CompilationUnit implements ICompilationUnit {
        String srcCode;
        protected String className;

        CompilationUnit(String srcCode, String className) {
            this.srcCode = srcCode;
            this.className = className;
        }

        @Override
        public char[] getFileName() {
            return className.toCharArray();
        }

        @Override
        public char[] getContents() {
            return srcCode.toCharArray();
        }

        @Override
        public char[] getMainTypeName() {
            return className.toCharArray();
        }

        @Override
        public char[][] getPackageName() {
            return new char[0][0];
        }

        @Override
        public boolean ignoreOptionalProblems() {
            return false;
        }
    }


    public static class CompilationUnitResult {
        private IProblem[] problems;


        public void reset() {
            problems = null;
        }
    }
}