package org.revapi.osgi;

import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class ExportPackageEntryParser {
    private static final Pattern COMMA = Pattern.compile(",");

    private ExportPackageEntryParser() {
        throw new AssertionError();
    }

    static void parse(String directive, Set<ExportPackageDefinition> packages) {
        ParserState.parsePackage(directive, packages);
    }

    private enum ParserState {
        //        Export-Package  ::= export ( ',' export )*
//        export          ::= package-names ( ';' parameter )*
//        package-names   ::= package-name  ( ';' package-name )*
//
//        WHERE:
//        ws            ::= <see Character.isWhitespace>
//        digit        ::= [0..9]
//        alpha        ::= [a..zA..Z]
//        alphanum     ::= alpha | digit
//        token        ::= ( alphanum | ’_’ | ’-’ )+
//        number       ::= digit+
//        jletter      ::= <see [3] Java Language Specification for JavaLetter>
//        jletterordigit::=  <See [3] Java Language Specification for JavaLetterOrDigit >
//        qname        ::= /* See [3] Java Language Specification for fully qualified class names */
//        identifier   ::= jletter jletterordigit *
//        extended     ::= ( alphanum | ’_’ | ’-’ | ’.’ )+
//        quoted-string ::= ’"’ ( ~["\#x0D#x0A#x00] | ’\"’|’\\’)* ’"’
//        argument     ::= extended  | quoted-string
//        parameter    ::= directive | attribute
//        directive    ::= extended ’:=’ argument
//        attribute    ::= extended ’=’ argument
//        unique-name  ::= identifier ( ’.’ identifier )*
//        symbolic-name ::= token('.'token)*
//                package-name ::= unique-name
//        path         ::= path-unquoted | (’"’ path-unquoted ’"’)
//        path-unquoted ::= path-sep | path-sep? path-element
//                (path-sep path-element)*
//        path-element ::= ~[/"\#x0D#x0A#x00]+
//        path-sep     ::= ’/’
//
//        WE'RE INTERESTED IN DIRECTIVES:
//        include,exclude
//        which are comma separated lists of class name patterns where the only supported special char is
//        '*' which is equal to regex '.*'
        EXPORT {
            @Override
            protected ParserState next(char c, Context ctx) {
                if (Character.isJavaIdentifierStart(c)) {
                    ctx.accumulate(c);
                    return PACKAGE;
                } else if (Character.isWhitespace(c)) {
                    return EXPORT;
                } else {
                    return ERROR;
                }
            }

            @Override
            protected void finalize(Context ctx) {
                ctx.exportDone();
            }
        },
        PACKAGE {
            @Override
            protected ParserState next(char c, Context ctx) {
                switch (c) {
                    case '.':
                        ctx.accumulate(c);
                        return PACKAGE;
                    case ';':
                        ctx.packageDone();
                        return EXPORT;
                    case ',':
                        ctx.packageDone();
                        ctx.exportDone();
                        return EXPORT;
                    case ':':
                        return MAYBE_DIRECTIVE_VALUE;
                    case '=':
                        //so this is not a package name after all, it is an attribute.. discard what we have
                        ctx.clearAccumulator();
                        return SKIP_PARAMETER;
                    default:
                        if (Character.isJavaIdentifierPart(c)) {
                            ctx.accumulate(c);
                            return PACKAGE;
                        } else {
                            return ERROR;
                        }
                }
            }

            @Override
            protected void finalize(Context ctx) {
                ctx.packageDone();
                ctx.exportDone();
            }
        },
        PARAMETER {
            @Override
            protected ParserState next(char c, Context ctx) {
                switch (c) {
                    case ':':
                        return MAYBE_DIRECTIVE_VALUE;
                    case '=':
                        ctx.clearAccumulator();
                        return SKIP_PARAMETER;
                    case ',':
                        ctx.exportDone();
                        return EXPORT;
                    default:
                        ctx.accumulate(c);
                        return PARAMETER;
                }
            }

            @Override
            protected void finalize(Context ctx) {
                ctx.exportDone();
            }
        },
        MAYBE_DIRECTIVE_VALUE {
            @Override
            protected ParserState next(char c, Context ctx) {
                if (c == '=') {
                    ctx.directiveNameDone();
                    return DIRECTIVE_VALUE_START;
                } else {
                    return ERROR;
                }
            }

            @Override
            protected void finalize(Context ctx) {
                ctx.exportDone();
            }
        },
        DIRECTIVE_VALUE_START {
            @Override
            protected ParserState next(char c, Context ctx) {
                switch (c) {
                    case '"':
                        return DIRECTIVE_VALUE_IN_QUOTES;
                    default:
                        ctx.accumulate(c);
                        return DIRECTIVE_VALUE;
                }
            }

            @Override
            protected void finalize(Context ctx) {
                ctx.directiveValueDone();
                ctx.exportDone();
            }
        },
        DIRECTIVE_VALUE {
            @Override
            protected ParserState next(char c, Context ctx) {
                switch (c) {
                    case ';':
                        ctx.directiveValueDone();
                        return PARAMETER;
                    case ',':
                        ctx.directiveValueDone();
                        ctx.exportDone();
                        return EXPORT;
                    default:
                        ctx.accumulate(c);
                        return DIRECTIVE_VALUE;
                }
            }

            @Override
            protected void finalize(Context ctx) {
                ctx.directiveValueDone();
                ctx.exportDone();
            }
        },
        DIRECTIVE_VALUE_IN_QUOTES {
            @Override
            protected ParserState next(char c, Context ctx) {
                switch (c) {
                    case '\\':
                        return DIRECTIVE_VALUE_IN_QUOTES_ESCAPE;
                    case '"':
                        ctx.directiveValueDone();
                        return EXPECT_DIRECTIVE_VALUE_END;
                    default:
                        ctx.accumulate(c);
                        return DIRECTIVE_VALUE_IN_QUOTES;
                }
            }

            @Override
            protected void finalize(Context ctx) {
                ctx.exportDone();
            }
        },
        EXPECT_DIRECTIVE_VALUE_END {
            @Override
            protected ParserState next(char c, Context ctx) {
                switch (c) {
                    case ';':
                        return PARAMETER;
                    case ',':
                        return EXPORT;
                    default:
                        return ERROR;
                }
            }

            @Override
            protected void finalize(Context ctx) {
                ctx.directiveValueDone();
                ctx.exportDone();
            }
        },
        DIRECTIVE_VALUE_IN_QUOTES_ESCAPE {
            @Override
            protected ParserState next(char c, Context ctx) {
                ctx.accumulate(c);
                return DIRECTIVE_VALUE_IN_QUOTES;
            }

            @Override
            protected void finalize(Context ctx) {
                ctx.exportDone();
            }
        },
        SKIP_PARAMETER {
            @Override
            protected ParserState next(char c, Context ctx) {
                switch (c) {
                    case ';':
                        return PARAMETER;
                    case '"':
                        return SKIP_PARAMETER_IN_QUOTES;
                    default:
                        return SKIP_PARAMETER;
                }
            }

            @Override
            protected void finalize(Context ctx) {
                ctx.exportDone();
            }
        },
        SKIP_PARAMETER_IN_QUOTES {
            @Override
            protected ParserState next(char c, Context ctx) {
                switch (c) {
                    case '\\':
                        return SKIP_IN_QUOTES_AFTER_ESCAPE;
                    case '"':
                        return EXPECT_DIRECTIVE_VALUE_END;
                    default:
                        return SKIP_PARAMETER_IN_QUOTES;
                }
            }

            @Override
            protected void finalize(Context ctx) {
            }
        },
        SKIP_IN_QUOTES_AFTER_ESCAPE {
            @Override
            protected ParserState next(char c, Context ctx) {
                return SKIP_PARAMETER_IN_QUOTES;
            }

            @Override
            protected void finalize(Context ctx) {
            }
        },
        ERROR {
            @Override
            protected ParserState next(char c, Context ctx) {
                return null;
            }

            @Override
            protected void finalize(Context ctx) {
            }
        };

        protected abstract ParserState next(char c, Context ctx);

        protected abstract void finalize(Context ctx);

        static void parsePackage(String directive, Set<ExportPackageDefinition> output) {
            Context ctx = new Context(output);
            ParserState state = EXPORT;
            if (directive != null) {
                for (int i = 0; i < directive.length(); ++i) {
                    char c = directive.charAt(i);
                    state = state.next(c, ctx);
                    if (state == ERROR) {
                        throw new IllegalArgumentException("Could not parse the Export-Package directive. Errored on index "
                                + i + " of directive:\n" + directive);
                    }
                }
            }
            state.finalize(ctx);
        }

        private static final class Context {
            final StringBuilder accumulator = new StringBuilder();
            final Set<ExportPackageDefinition> output;

            boolean isInclude;
            boolean isExclude;
            List<String> packages = new ArrayList<>(2);
            List<String> include = new ArrayList<>(2);
            List<String> exclude = new ArrayList<>(2);

            Context(Set<ExportPackageDefinition> output) {
                this.output = output;
            }

            void accumulate(char c) {
                accumulator.append(c);
            }

            void clearAccumulator() {
                accumulator.setLength(0);
            }

            void directiveNameDone() {
                String directiveName = accumulator.toString();
                clearAccumulator();
                isInclude = false;
                isExclude = false;

                if ("include".equals(directiveName)) {
                    isInclude = true;
                } else if ("exclude".equals(directiveName)) {
                    isExclude = true;
                }
            }

            void packageDone() {
                packages.add(accumulator.toString());
                clearAccumulator();
            }

            void directiveValueDone() {
                if (isInclude) {
                    include.addAll(splitAndTrim(accumulator.toString()));
                } else if (isExclude) {
                    exclude.addAll(splitAndTrim(accumulator.toString()));
                }
                clearAccumulator();
                isExclude = false;
                isInclude = false;
            }

            void exportDone() {
                output.add(new ExportPackageDefinition(packages, toPatterns(include), toPatterns(exclude)));
                packages.clear();
                include.clear();
                exclude.clear();
                clearAccumulator();
            }

            private List<String> splitAndTrim(String str) {
                return Stream.of(COMMA.split(str)).map(String::trim).collect(toList());
            }

            private Pattern toPattern(String classFilter) {
                return Pattern.compile(classFilter.replace("*", ".*"));
            }

            private Set<Pattern> toPatterns(List<String> classFilters) {
                return classFilters.stream().map(this::toPattern).collect(Collectors.toSet());
            }
        }
    }
}
