package banjo.ui.text;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.Assert;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.jface.text.rules.ITokenScanner;
import org.eclipse.jface.text.rules.Token;

import banjo.analysis.DefInfo;
import banjo.analysis.DefRefScanner;
import banjo.desugar.BanjoDesugarer;
import banjo.desugar.IncrementalUpdater;
import banjo.dom.Comment;
import banjo.dom.CoreExpr;
import banjo.dom.Ellipsis;
import banjo.dom.HasFileRange;
import banjo.dom.Identifier;
import banjo.dom.Key;
import banjo.dom.NumberLiteral;
import banjo.dom.OperatorRef;
import banjo.dom.StringLiteral;
import banjo.dom.TokenVisitor;
import banjo.dom.UnitRef;
import banjo.dom.Whitespace;
import banjo.parser.BanjoParser;
import banjo.parser.BanjoScanner;
import banjo.parser.errors.UnexpectedIOExceptionError;
import banjo.parser.util.FileRange;
import banjo.parser.util.ParserReader;

public class SourceScanner implements ITokenScanner {

	private IDocument document;
	
	private char[][] legalLineDelimiters;
	final IToken commentToken;
	final IToken operatorToken;
	final IToken identifierToken;
	final IToken unresolvedIdentifierToken;
	final IToken stringLiteralToken;
	final IToken numberLiteralToken;
	final IToken fieldToken;
	final IToken defaultToken;
	final IToken localFunctionToken;
	final IToken localConstToken;
	final IToken localValueToken;
	final IToken parameterToken;
	final IToken selfToken;
	final IToken selfFieldToken;
	final IToken selfConstToken;
	final IToken selfMethodToken;
	private final IToken local1, local2, local3, local4, local5;
	private final IToken parameter1, parameter2, parameter3, parameter4, parameter5;
	private final IToken field1, field2, field3, field4, field5;
	private final IToken self1, self2, self3, self4, self5;
	private final IToken function1, function2, function3, function4, function5;
	
	private final BanjoScanner scanner = new BanjoScanner();
	private final BanjoParser parser = new BanjoParser();
	private final BanjoDesugarer desugarer = new BanjoDesugarer();
	private final IncrementalUpdater astUpdater = new IncrementalUpdater();
	private final DefRefScanner defRefScanner = new DefRefScanner();
	private CoreExpr ast = null;
	private final Map<FileRange,DefInfo> tokenDefMap = new HashMap<>();
	private Set<FileRange> unusedDefs = new HashSet<>();
	private ParserReader in = null;

	private int tokenOffset;

	private int tokenLength;

	private boolean inProjection;


	
	public SourceScanner(BanjoStyleManager manager) {
		commentToken = new Token(manager.getStyle(BanjoStyleConstants.COMMENT));
		operatorToken = new Token(manager.getStyle(BanjoStyleConstants.OPERATOR));
		identifierToken = new Token(manager.getStyle(BanjoStyleConstants.IDENTIFIER));
		unresolvedIdentifierToken = new Token(manager.getStyle(BanjoStyleConstants.UNRESOLVED_IDENTIFIER));
		stringLiteralToken = new Token(manager.getStyle(BanjoStyleConstants.STRING_LITERAL));
		numberLiteralToken = new Token(manager.getStyle(BanjoStyleConstants.NUMBER_LITERAL));
		fieldToken = new Token(manager.getStyle(BanjoStyleConstants.FIELD));
		defaultToken = new Token(manager.getStyle(BanjoStyleConstants.DEFAULT));
		localFunctionToken = new Token(manager.getStyle(BanjoStyleConstants.LOCAL_FUNCTION));
		localConstToken = new Token(manager.getStyle(BanjoStyleConstants.LOCAL_CONST));
		localValueToken = new Token(manager.getStyle(BanjoStyleConstants.LOCAL_VALUE));
		parameterToken = new Token((manager.getStyle(BanjoStyleConstants.PARAMETER)));
		selfToken = new Token(manager.getStyle(BanjoStyleConstants.SELF));
		selfFieldToken = new Token(manager.getStyle(BanjoStyleConstants.SELF_FIELD));
		selfConstToken = new Token(manager.getStyle(BanjoStyleConstants.SELF_FIELD));
		selfMethodToken = new Token(manager.getStyle(BanjoStyleConstants.SELF_METHOD));
		
		local1 = new Token(manager.getStyle(BanjoStyleConstants.LOCAL_1));
		local2 = new Token(manager.getStyle(BanjoStyleConstants.LOCAL_2));
		local3 = new Token(manager.getStyle(BanjoStyleConstants.LOCAL_3));
		local4 = new Token(manager.getStyle(BanjoStyleConstants.LOCAL_4));
		local5 = new Token(manager.getStyle(BanjoStyleConstants.LOCAL_5));

		function1 = new Token(manager.getStyle(BanjoStyleConstants.LOCAL_1));
		function2 = new Token(manager.getStyle(BanjoStyleConstants.LOCAL_2));
		function3 = new Token(manager.getStyle(BanjoStyleConstants.LOCAL_3));
		function4 = new Token(manager.getStyle(BanjoStyleConstants.LOCAL_4));
		function5 = new Token(manager.getStyle(BanjoStyleConstants.LOCAL_5));
		
		parameter1 = new Token(manager.getStyle(BanjoStyleConstants.PARAMETER_1));
		parameter2 = new Token(manager.getStyle(BanjoStyleConstants.PARAMETER_2));
		parameter3 = new Token(manager.getStyle(BanjoStyleConstants.PARAMETER_3));
		parameter4 = new Token(manager.getStyle(BanjoStyleConstants.PARAMETER_4));
		parameter5 = new Token(manager.getStyle(BanjoStyleConstants.PARAMETER_5));

		field1 = new Token(manager.getStyle(BanjoStyleConstants.FIELD_1));
		field2 = new Token(manager.getStyle(BanjoStyleConstants.FIELD_2));
		field3 = new Token(manager.getStyle(BanjoStyleConstants.FIELD_3));
		field4 = new Token(manager.getStyle(BanjoStyleConstants.FIELD_4));
		field5 = new Token(manager.getStyle(BanjoStyleConstants.FIELD_5));

		self1 = new Token(manager.getStyle(BanjoStyleConstants.SELF_1));
		self2 = new Token(manager.getStyle(BanjoStyleConstants.SELF_2));
		self3 = new Token(manager.getStyle(BanjoStyleConstants.SELF_3));
		self4 = new Token(manager.getStyle(BanjoStyleConstants.SELF_4));
		self5 = new Token(manager.getStyle(BanjoStyleConstants.SELF_5));
		
	}

	/*
	 * @see ITokenScanner#setRange(IDocument, int, int)
	 */
	public void setRange(final IDocument document, int offset, int length) {
		Assert.isLegal(document != null);
		final int documentLength= document.getLength();
		checkRange(offset, length, documentLength);

		setDocument(document);
		
		this.in = ParserReader.fromSubstring("", document.get(), offset, offset+length);
		String[] delimiters = document.getLegalLineDelimiters();
		this.legalLineDelimiters = new char[delimiters.length][];
		for (int i= 0; i < delimiters.length; i++)
			this.legalLineDelimiters[i] = delimiters[i].toCharArray();
	}

	public void setDocument(final IDocument document) {
		if(document == this.document)
			return;
		if(this.document != null) {
			this.document.removeDocumentListener(documentListener);
			this.ast = null;
		}
		if(document != null) {
			document.addDocumentListener(documentListener);		
			analyseSource(document);
		}
	}


	private void analyseSource(IDocument document) {
		try {
			ast = desugarer.desugar(parser.parse(ParserReader.fromString("", document.get())));
			defRefScanner.buildTokenDefMap(ast, tokenDefMap, unusedDefs );
		} catch (IOException e) {
			throw new UnexpectedIOExceptionError(e);
		}
	}

	/**
	 * Checks that the given range is valid.
	 * See https://bugs.eclipse.org/bugs/show_bug.cgi?id=69292
	 *
	 * @param offset the offset of the document range to scan
	 * @param length the length of the document range to scan
	 * @param documentLength the document's length
	 * @since 3.3
	 */
	private void checkRange(int offset, int length, int documentLength) {
		Assert.isLegal(offset > -1);
		Assert.isLegal(length > -1);
		Assert.isLegal(offset + length <= documentLength);
	}

	@Override
	public IToken nextToken() {
		try {
			return scanner.next(this.in, tokenVisitor);
		} catch (IOException e) {
			throw new Error(e); // Shouldn't happen with a string as input
		}
	}

	@Override
	public int getTokenOffset() {
		return tokenOffset;
	}

	@Override
	public int getTokenLength() {
		return tokenLength;
	}

	private final TokenVisitor<IToken> tokenVisitor = new TokenVisitor<IToken>() {
		IToken token(IToken token, int offset, int length) {
			tokenOffset = offset;
			tokenLength = length;
			if(token != Token.WHITESPACE && token != commentToken)
				inProjection = false;
			return token;
		}
		
		IToken token(IToken token, HasFileRange sourceToken) {
			return token(token, sourceToken.getStartOffset(), sourceToken.getFileRange().length());
		}
		
		IToken token(IToken normalToken, IToken fieldToken, Key key) {
			return token(inProjection?fieldToken:normalToken, key);
		}
		
		@Override
		public IToken visitWhitespace(Whitespace ws) {
			return token(Token.WHITESPACE, ws);
		}

		@Override
		public IToken visitComment(Comment com) {
			return token(commentToken, com);
		}

		@Override
		public IToken visitEof(FileRange entireFileRange) {
			return token(Token.EOF, tokenOffset+tokenLength, 0);
		}

		@Override
		public IToken visitOperator(OperatorRef or) {
			IToken result = token(operatorToken, or);
			if(or.getOp().equals(".")) {
				inProjection = true;
			}
			return result;
		}

		@Override
		public IToken visitStringLiteral(StringLiteral lit) {
			return token(stringLiteralToken, fieldToken, lit);
		}

		@Override
		public IToken visitNumberLiteral(NumberLiteral lit) {
			return token(numberLiteralToken, lit);
		}

		@Override
		public IToken visitIdentifier(Identifier ident) {
			DefInfo def = tokenDefMap.get(ident.getFileRange());
			if(def != null) {
				switch(def.getType()) {
				case SELF_FIELD:
				case FIELD: {
					switch(def.getScopeDepth()%5) {
					default:
					case 0: return token(field1, ident); 
					case 1: return token(field2, ident); 
					case 2: return token(field3, ident); 
					case 3: return token(field4, ident); 
					case 4: return token(field5, ident); 
					}
				}
				case LOCAL_FUNCTION: {
					switch(def.getScopeDepth()%5) {
					default:
					case 0: return token(function1, ident); 
					case 1: return token(function2, ident); 
					case 2: return token(function3, ident); 
					case 3: return token(function4, ident); 
					case 4: return token(function5, ident); 
					}
				}
				case LOCAL_CONST:
				case LOCAL_VALUE: {
					switch(def.getScopeDepth()%5) {
					default:
					case 0: return token(local1, ident); 
					case 1: return token(local2, ident); 
					case 2: return token(local3, ident); 
					case 3: return token(local4, ident); 
					case 4: return token(local5, ident); 
					}
				}
				case PARAMETER: {
					switch(def.getScopeDepth()%5) {
					default:
					case 0: return token(parameter1, ident); 
					case 1: return token(parameter2, ident); 
					case 2: return token(parameter3, ident); 
					case 3: return token(parameter4, ident); 
					case 4: return token(parameter5, ident); 
					}
				}
				case SELF: {
					switch(def.getScopeDepth()%5) {
					default:
					case 0: return token(self1, ident); 
					case 1: return token(self2, ident); 
					case 2: return token(self3, ident); 
					case 3: return token(self4, ident); 
					case 4: return token(self5, ident); 
					}
				}
				case SELF_CONST: return token(selfConstToken, ident);
				case SELF_METHOD: return token(selfMethodToken, ident);
				}
			}
			return token(unresolvedIdentifierToken, fieldToken, ident);
		}

		@Override
		public IToken visitEllipsis(Ellipsis ellipsis) {
			return token(identifierToken, ellipsis);
		}

		@Override
		public IToken visitUnit(UnitRef unit) {
			return token(identifierToken, unit);
		}
	};
	
	final IDocumentListener documentListener = new IDocumentListener() {
		
		@Override
		public void documentChanged(DocumentEvent event) {
			if(event.getDocument() == document) {
				CoreExpr oldAst = ast;
				CoreExpr newAst = ast = astUpdater.applyEdit(ast, event.getOffset(), event.getLength(), event.getText(), document.get());
				defRefScanner.updateTokenDefMap(oldAst, newAst, tokenDefMap, unusedDefs);
			}
		}
		
		@Override
		public void documentAboutToBeChanged(DocumentEvent event) {	}
	};
	
}
