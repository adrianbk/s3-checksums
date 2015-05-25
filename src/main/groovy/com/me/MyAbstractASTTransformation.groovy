package com.me

import org.codehaus.groovy.GroovyBugError
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.AnnotatedNode
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.PropertyExpression
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.control.messages.Message
import org.codehaus.groovy.control.messages.WarningMessage
import org.codehaus.groovy.syntax.Token
import org.codehaus.groovy.syntax.Types
import org.codehaus.groovy.transform.AbstractASTTransformation

import java.lang.annotation.Annotation

abstract class MyAbstractASTTransformation extends AbstractASTTransformation {
  Class<? extends Annotation> annotationClass
  ClassNode annotationClassNode
  AnnotatedNode annotatedNode
  AnnotationNode annotationNode

  boolean verificationFailed

  MyAbstractASTTransformation(Class<? extends Annotation> annotationClass) {
    this.annotationClass = annotationClass
    annotationClassNode = ClassHelper.make(annotationClass)
  }

  String getAnnotationClassName() {
    '@'+annotationClass.simpleName
  }

  void initialize(ASTNode[] nodes, SourceUnit sourceUnit) {
    init(nodes, sourceUnit)

    this.sourceUnit = sourceUnit
    this.annotationClass = annotationClass
    verificationFailed = false

    setNodes(nodes)
    if (verificationFailed) {
      throw new GroovyBugError("AST nodes verification failed")
    }
  }

  def setNodes(ASTNode[] nodes) {
    verify "ASTNode array should not be null", nodes != null
    verify "ASTNode array length should be 2", nodes.length == 2
    verify "First ASTNode should be an annotation node", nodes[0] instanceof AnnotationNode
    verify "First ASTNode should be annotation for $annotationClass", nodes[0].classNode?.name == annotationClass.name
    verify "Second ASTNode should be an annotated node", nodes[1] instanceof AnnotatedNode
    annotationNode = nodes[0] as AnnotationNode
    annotatedNode = nodes[1] as AnnotatedNode
  }

  def verify(String message, boolean shouldBeTrue, AnnotatedNode node = annotatedNode) {
    if (!shouldBeTrue) {
      verificationFailed = true
      if (node) {
        addError(message, node)
      } else {
        addError(message)
      }
    }
  }

  Object getMemberValue(AnnotationNode node, String name) {
    getMemberValue(node, name, null)
  }

  Object getMemberValue(AnnotationNode node, String name, Object defaultValue) {
    Expression member = node.getMember(name)
    if (member != null) {
      if (member instanceof PropertyExpression) {
        def property =(PropertyExpression)member
        member = property.property
      }
      if (member instanceof ConstantExpression) {
        Object result = ((ConstantExpression) member).getValue()
        if (result != null) {
          return result
        }
      }
    }
    return defaultValue
  }

  void addError(String message) {
    sourceUnit.errorCollector.addError(Message.create(message, sourceUnit))
  }

  void addWarning(String message, ASTNode node, int level = WarningMessage.NONE) {
    sourceUnit.errorCollector.addWarning(
            level, // note warning won't display if level is *higher* than configured limit, i.e. NONE will always be displayed
            message,
            new Token(Types.UNKNOWN, node.text, node.lineNumber, node.columnNumber),
            sourceUnit)
  }

  void info(String message, String annotatedPartialClassName = null) {
    if (annotatedPartialClassName == null || annotatedNode.text.contains(annotatedPartialClassName)) {
      addWarning(message, annotatedNode)
    }
  }

}