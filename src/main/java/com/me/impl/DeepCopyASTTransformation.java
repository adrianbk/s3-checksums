package com.me.impl;

import com.me.DeepCopy;
import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.ast.expr.ConstructorCallExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.ast.stmt.ReturnStatement;
import org.codehaus.groovy.ast.tools.GeneralUtils;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.transform.AbstractASTTransformation;
import org.codehaus.groovy.transform.GroovyASTTransformation;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
public class DeepCopyASTTransformation extends AbstractASTTransformation {

    static final ClassNode MY_TYPE = ClassHelper.make(DeepCopy.class);
    static final ClassNode SERIALIZABLE_TYPE = ClassHelper.make(Serializable.class);

    static final String MY_TYPE_NAME = "@" + MY_TYPE.getNameWithoutPackage();

    private static final ClassNode BYTE_ARRAY_INPUT_STREAM_TYPE = ClassHelper.make(ByteArrayInputStream.class);
    private static final ClassNode BYTE_ARRAY_OUTPUT_STREAM_TYPE = ClassHelper.make(ByteArrayOutputStream.class);
    private static final ClassNode OBJECT_INPUT_STREAM_TYPE = ClassHelper.make(ObjectInputStream.class);
    private static final ClassNode OBJECT_OUTPUT_STREAM_TYPE = ClassHelper.make(ObjectOutputStream.class);

    public void visit(ASTNode[] nodes, SourceUnit source) {
        init(nodes, source);
        AnnotationNode annotationNode = (AnnotationNode) nodes[0];
        AnnotatedNode parent = (AnnotatedNode) nodes[1];
        if (annotationNode == null || !MY_TYPE.equals(annotationNode.getClassNode())) {
            return;
        }

        if (parent instanceof ClassNode) {
            ClassNode cNode = (ClassNode) parent;
            checkNotInterface(cNode, MY_TYPE_NAME);
            createDeepCopy(cNode);
        }
    }

    private void createDeepCopy(ClassNode cNode) {
        if (GeneralUtils.hasDeclaredMethod(cNode, "deepCopy", 0)) {
            return;
        }

        // make sure class node implements Serializable
        cNode.addInterface(SERIALIZABLE_TYPE);

        BlockStatement body = buildDeepCopyMethodBody();
        ClassNode returnType = cNode.getPlainNodeReference();
        cNode.addMethod("deepCopy", ACC_PUBLIC, returnType, Parameter.EMPTY_ARRAY, ClassNode.EMPTY_ARRAY, body);
    }

    private BlockStatement buildDeepCopyMethodBody() {
        BlockStatement body = new BlockStatement();

        // def bos = new ByteArrayOutputStream()
        Expression bos = new VariableExpression("bos");
        Expression newBos = new ConstructorCallExpression(BYTE_ARRAY_OUTPUT_STREAM_TYPE, MethodCallExpression.NO_ARGUMENTS);
        body.addStatement(GeneralUtils.declS(bos, newBos));

        // def oos = new ObjectOutputStream(bos)
        Expression oos = new VariableExpression("oos");
        Expression newOos = new ConstructorCallExpression(OBJECT_OUTPUT_STREAM_TYPE, bos);
        body.addStatement(GeneralUtils.declS(oos, newOos));

        // oos.writeObject(this)
        Expression oosWriteObject = new MethodCallExpression(oos, "writeObject", VariableExpression.THIS_EXPRESSION);
        body.addStatement(new ExpressionStatement(oosWriteObject));

        // oos.flush()
        Expression oosFlush = new MethodCallExpression(oos, "flush", MethodCallExpression.NO_ARGUMENTS);
        body.addStatement(new ExpressionStatement(oosFlush));

        // def bin = new ByteArrayInputStream(bos.toByteArray())
        Expression bin = new VariableExpression("bin");
        Expression bosToByteArray = new MethodCallExpression(bos, "toByteArray", MethodCallExpression.NO_ARGUMENTS);
        Expression newBin = new ConstructorCallExpression(BYTE_ARRAY_INPUT_STREAM_TYPE, bosToByteArray);
        body.addStatement(GeneralUtils.declS(bin, newBin));

        // def ois = new ObjectInputStream(bin)
        Expression ois = new VariableExpression("ois");
        Expression newOis = new ConstructorCallExpression(OBJECT_INPUT_STREAM_TYPE, bin);
        body.addStatement(GeneralUtils.declS(ois, newOis));

        // return ois.readObject()
        Expression oisReadObject = new MethodCallExpression(ois, "readObject", MethodCallExpression.NO_ARGUMENTS);
        body.addStatement(new ReturnStatement(oisReadObject));

        return body;
    }

}