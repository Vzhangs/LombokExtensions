package lombok.javac.handlers;

import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import lombok.Lombok;
import lombok.Nullable;
import lombok.core.AnnotationValues;
import lombok.core.HandlerPriority;
import lombok.spi.Provides;

import java.io.PrintStream;

import static lombok.javac.Javac.*;
import static lombok.javac.handlers.HandleDelegate.HANDLE_DELEGATE_PRIORITY;
import static lombok.javac.handlers.JavacHandlerUtil.*;


@Provides
@HandlerPriority(HANDLE_DELEGATE_PRIORITY + 99)
public class HandleNullable extends JavacAnnotationHandler<Nullable> {

    private static boolean eq(String typeTreeToString, String key) {
        return typeTreeToString.equals(key) || typeTreeToString.equals("lombok." + key) || typeTreeToString.equals("lombok.experimental." + key);
    }

    @Override
    public void handle(final AnnotationValues<Nullable> annotation, final JCAnnotation ast, final JavacNode annotationNode) {
        if (inNetbeansEditor(annotationNode)) return;

        switch (annotationNode.up().getKind()) {
            case METHOD:
                handleMethodNullable(annotation, ast, annotationNode);
                break;
            case LOCAL:
                handleLocalNullable(annotation, ast, annotationNode);
                break;
            default:
                annotationNode.addError("@Nullable is only supported on method parameters and local variables.");
                return;
        }

    }

    private void handleMethodNullable(final AnnotationValues<Nullable> annotation, final JCAnnotation ast, final JavacNode annotationNode) {
        // TODO
    }

    private void handleLocalNullable(final AnnotationValues<Nullable> annotation, final JCAnnotation ast, final JavacNode annotationNode) {
        JCVariableDecl decl = (JCVariableDecl) annotationNode.up().get();
        if (decl.init == null) {
            annotationNode.addError("@Nullable variable declarations need to be initialized.");
            return;
        }

        // 获取被Nullable修饰的语句的上级语句块
        JavacNode ancestor = annotationNode.up().directUp();
        JCTree blockNode = ancestor.get();
        final List<JCStatement> statements;
        if (blockNode instanceof JCBlock) {
            statements = ((JCBlock) blockNode).stats;
        } else if (blockNode instanceof JCCase) {
            statements = ((JCCase) blockNode).stats;
        } else if (blockNode instanceof JCMethodDecl) {
            statements = ((JCMethodDecl) blockNode).body.stats;
        } else {
            annotationNode.addError("@Nullable is legal only on a local variable declaration inside a block.");
            return;
        }

        // 获取被 @Nullable 修饰的语句前后的语句
        boolean seenDeclaration = false;
        ListBuffer<JCStatement> beforeStatements = new ListBuffer<JCStatement>();
        ListBuffer<JCStatement> afterStatements = new ListBuffer<JCStatement>();
        for (JCStatement statement : statements) {
            if (statement == decl){
                seenDeclaration = true;
                continue;
            }
            if (!seenDeclaration) {
                beforeStatements.append(statement);
            } else {
                afterStatements.append(statement);
            }
        }

        JavacTreeMaker maker = annotationNode.getTreeMaker();

        // 构建 try-catch 语句
        ListBuffer<JCAnnotation> newAnnotations = new ListBuffer<JCAnnotation>();
        for (JCAnnotation anno : decl.mods.annotations) {
            if (anno.getAnnotationType().toString().endsWith("Nullable")) {
                continue;
            }
            newAnnotations.append(anno);
        }
        decl.mods.annotations = newAnnotations.toList();
        JCVariableDecl defDecl = maker.VarDef(decl.mods, decl.name, decl.vartype, null);

        JCStatement tryAssign = maker.Exec(maker.Assign(maker.Ident(decl.name), decl.getInitializer()));

        JCStatement catchAssign = maker.Exec(maker.Assign(maker.Ident(decl.name), createDefaultInitializer(defDecl, maker)));

        JCVariableDecl exceptionDef = maker.VarDef(
                maker.Modifiers(Flags.FINAL | Flags.PARAMETER),
                annotationNode.toName("e"),
                chainDots(annotationNode, "java.lang.NullPointerException".split("\\.")),
                null
        );

        JCTry tryStatement = maker.Try(
                maker.Block(0, List.of(tryAssign)),
                List.of(maker.Catch(exceptionDef, maker.Block(0, List.of(catchAssign)))),
                maker.Block(0, List.<JCStatement>nil())
        );

        // 构建新代码块
        ListBuffer<JCStatement> newStatements = new ListBuffer<JCStatement>();
        newStatements.appendList(beforeStatements);
        newStatements.append(defDecl);
        newStatements.append(tryStatement);
        newStatements.appendList(afterStatements);


        if (blockNode instanceof JCBlock) {
            ((JCBlock)blockNode).stats = newStatements.toList();
        } else if (blockNode instanceof JCCase) {
            ((JCCase)blockNode).stats = newStatements.toList();
        } else if (blockNode instanceof JCMethodDecl) {
            ((JCMethodDecl)blockNode).body.stats = newStatements.toList();
        } else throw new AssertionError("Should not get here");
        System.out.println(annotationNode.up().up().get());
        ancestor.rebuild();
    }

    private JCExpression createDefaultInitializer(JCVariableDecl defDecl, JavacTreeMaker maker) {
        if (defDecl.vartype instanceof JCPrimitiveTypeTree) {
            switch (((JCPrimitiveTypeTree) defDecl.vartype).getPrimitiveTypeKind()) {
                case BOOLEAN:
                    return maker.Literal(CTC_BOOLEAN, 0);
                case CHAR:
                    return maker.Literal(CTC_CHAR, 0);
                default:
                case BYTE:
                case SHORT:
                case INT:
                    return maker.Literal(CTC_INT, 0);
                case LONG:
                    return maker.Literal(CTC_LONG, 0L);
                case FLOAT:
                    return maker.Literal(CTC_FLOAT, 0F);
                case DOUBLE:
                    return maker.Literal(CTC_DOUBLE, 0D);
            }
        }
        return maker.Literal(CTC_BOT, null);
    }


    private void printAST(final AnnotationValues<Nullable> annotation, final JCAnnotation ast, final JavacNode annotationNode) {
        PrintStream stream = System.out;
        try {
            annotationNode.up().traverse(new JavacASTVisitor.Printer(true, stream));
        } finally {
            if (stream != System.out) {
                try {
                    stream.close();
                } catch (Exception e) {
                    throw Lombok.sneakyThrow(e);
                }
            }
        }
    }
}
