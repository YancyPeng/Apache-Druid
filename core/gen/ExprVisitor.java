// Generated from /Users/yangjun.peng/IdeaProjects/druid/core/src/main/antlr4/org/apache/druid/math/expr/antlr/Expr.g4 by ANTLR 4.13.1
import org.antlr.v4.runtime.tree.ParseTreeVisitor;

/**
 * This interface defines a complete generic visitor for a parse tree produced
 * by {@link ExprParser}.
 *
 * @param <T> The return type of the visit operation. Use {@link Void} for
 * operations with no return type.
 */
public interface ExprVisitor<T> extends ParseTreeVisitor<T> {
	/**
	 * Visit a parse tree produced by {@link ExprParser#start}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStart(ExprParser.StartContext ctx);
	/**
	 * Visit a parse tree produced by the {@code applyFunctionExpr}
	 * labeled alternative in {@link ExprParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitApplyFunctionExpr(ExprParser.ApplyFunctionExprContext ctx);
	/**
	 * Visit a parse tree produced by the {@code doubleExpr}
	 * labeled alternative in {@link ExprParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDoubleExpr(ExprParser.DoubleExprContext ctx);
	/**
	 * Visit a parse tree produced by the {@code doubleArray}
	 * labeled alternative in {@link ExprParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDoubleArray(ExprParser.DoubleArrayContext ctx);
	/**
	 * Visit a parse tree produced by the {@code addSubExpr}
	 * labeled alternative in {@link ExprParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAddSubExpr(ExprParser.AddSubExprContext ctx);
	/**
	 * Visit a parse tree produced by the {@code string}
	 * labeled alternative in {@link ExprParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitString(ExprParser.StringContext ctx);
	/**
	 * Visit a parse tree produced by the {@code longExpr}
	 * labeled alternative in {@link ExprParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitLongExpr(ExprParser.LongExprContext ctx);
	/**
	 * Visit a parse tree produced by the {@code explicitStringArray}
	 * labeled alternative in {@link ExprParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExplicitStringArray(ExprParser.ExplicitStringArrayContext ctx);
	/**
	 * Visit a parse tree produced by the {@code logicalAndOrExpr}
	 * labeled alternative in {@link ExprParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitLogicalAndOrExpr(ExprParser.LogicalAndOrExprContext ctx);
	/**
	 * Visit a parse tree produced by the {@code longArray}
	 * labeled alternative in {@link ExprParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitLongArray(ExprParser.LongArrayContext ctx);
	/**
	 * Visit a parse tree produced by the {@code nestedExpr}
	 * labeled alternative in {@link ExprParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitNestedExpr(ExprParser.NestedExprContext ctx);
	/**
	 * Visit a parse tree produced by the {@code logicalOpExpr}
	 * labeled alternative in {@link ExprParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitLogicalOpExpr(ExprParser.LogicalOpExprContext ctx);
	/**
	 * Visit a parse tree produced by the {@code functionExpr}
	 * labeled alternative in {@link ExprParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFunctionExpr(ExprParser.FunctionExprContext ctx);
	/**
	 * Visit a parse tree produced by the {@code explicitLongArray}
	 * labeled alternative in {@link ExprParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExplicitLongArray(ExprParser.ExplicitLongArrayContext ctx);
	/**
	 * Visit a parse tree produced by the {@code unaryOpExpr}
	 * labeled alternative in {@link ExprParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitUnaryOpExpr(ExprParser.UnaryOpExprContext ctx);
	/**
	 * Visit a parse tree produced by the {@code null}
	 * labeled alternative in {@link ExprParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitNull(ExprParser.NullContext ctx);
	/**
	 * Visit a parse tree produced by the {@code stringArray}
	 * labeled alternative in {@link ExprParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStringArray(ExprParser.StringArrayContext ctx);
	/**
	 * Visit a parse tree produced by the {@code mulDivModuloExpr}
	 * labeled alternative in {@link ExprParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMulDivModuloExpr(ExprParser.MulDivModuloExprContext ctx);
	/**
	 * Visit a parse tree produced by the {@code powOpExpr}
	 * labeled alternative in {@link ExprParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPowOpExpr(ExprParser.PowOpExprContext ctx);
	/**
	 * Visit a parse tree produced by the {@code identifierExpr}
	 * labeled alternative in {@link ExprParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitIdentifierExpr(ExprParser.IdentifierExprContext ctx);
	/**
	 * Visit a parse tree produced by {@link ExprParser#lambda}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitLambda(ExprParser.LambdaContext ctx);
	/**
	 * Visit a parse tree produced by the {@code functionArgs}
	 * labeled alternative in {@link ExprParser#fnArgs}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFunctionArgs(ExprParser.FunctionArgsContext ctx);
	/**
	 * Visit a parse tree produced by {@link ExprParser#stringElement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStringElement(ExprParser.StringElementContext ctx);
	/**
	 * Visit a parse tree produced by {@link ExprParser#longElement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitLongElement(ExprParser.LongElementContext ctx);
	/**
	 * Visit a parse tree produced by {@link ExprParser#numericElement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitNumericElement(ExprParser.NumericElementContext ctx);
	/**
	 * Visit a parse tree produced by {@link ExprParser#literalElement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitLiteralElement(ExprParser.LiteralElementContext ctx);
}