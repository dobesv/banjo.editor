package banjo.ui.text;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.Assert;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.jface.text.rules.ITokenScanner;
import org.eclipse.jface.text.rules.Token;

import banjo.analysis.DefRefAnalyser;
import banjo.analysis.DefRefAnalyser.Analysis;
import banjo.analysis.DefRefAnalyser.SourceRangeAnalysis;
import banjo.desugar.BanjoDesugarer;
import banjo.desugar.BanjoDesugarer.DesugarResult;
import banjo.desugar.IncrementalUpdater;
import banjo.dom.core.CoreExpr;
import banjo.dom.token.BadToken;
import banjo.dom.token.Comment;
import banjo.dom.token.Ellipsis;
import banjo.dom.token.Identifier;
import banjo.dom.token.NumberLiteral;
import banjo.dom.token.OperatorRef;
import banjo.dom.token.StringLiteral;
import banjo.dom.token.TokenVisitor;
import banjo.dom.token.Whitespace;
import banjo.parser.BanjoParser;
import banjo.parser.BanjoParser.ExtSourceExpr;
import banjo.parser.BanjoScanner;
import banjo.parser.util.FileRange;
import banjo.parser.util.OffsetLength;
import banjo.parser.util.ParserReader;
import banjo.parser.util.UnexpectedIOExceptionError;
import fj.P2;

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
	private final IncrementalUpdater astUpdater = new IncrementalUpdater();
	private DesugarResult<CoreExpr> desugarResult;
	private CoreExpr ast = null;
	Analysis defRefAnalysis;
	private SourceRangeAnalysis sourceRangeAnalysis;
	private ParserReader in = null;

	private int tokenOffset;

	private int tokenLength;

	private boolean inProjection;

	private ExtSourceExpr parseResult;




	public SourceScanner(BanjoStyleManager manager) {
		this.commentToken = new Token(manager.getStyle(BanjoStyleConstants.COMMENT));
		this.operatorToken = new Token(manager.getStyle(BanjoStyleConstants.OPERATOR));
		this.identifierToken = new Token(manager.getStyle(BanjoStyleConstants.IDENTIFIER));
		this.unresolvedIdentifierToken = new Token(manager.getStyle(BanjoStyleConstants.UNRESOLVED_IDENTIFIER));
		this.stringLiteralToken = new Token(manager.getStyle(BanjoStyleConstants.STRING_LITERAL));
		this.numberLiteralToken = new Token(manager.getStyle(BanjoStyleConstants.NUMBER_LITERAL));
		this.fieldToken = new Token(manager.getStyle(BanjoStyleConstants.FIELD));
		this.defaultToken = new Token(manager.getStyle(BanjoStyleConstants.DEFAULT));
		this.localFunctionToken = new Token(manager.getStyle(BanjoStyleConstants.LOCAL_FUNCTION));
		this.localConstToken = new Token(manager.getStyle(BanjoStyleConstants.LOCAL_CONST));
		this.localValueToken = new Token(manager.getStyle(BanjoStyleConstants.LOCAL_VALUE));
		this.parameterToken = new Token((manager.getStyle(BanjoStyleConstants.PARAMETER)));
		this.selfToken = new Token(manager.getStyle(BanjoStyleConstants.SELF));
		this.selfFieldToken = new Token(manager.getStyle(BanjoStyleConstants.SELF_FIELD));
		this.selfConstToken = new Token(manager.getStyle(BanjoStyleConstants.SELF_FIELD));
		this.selfMethodToken = new Token(manager.getStyle(BanjoStyleConstants.SELF_METHOD));

		this.local1 = new Token(manager.getStyle(BanjoStyleConstants.LOCAL_1));
		this.local2 = new Token(manager.getStyle(BanjoStyleConstants.LOCAL_2));
		this.local3 = new Token(manager.getStyle(BanjoStyleConstants.LOCAL_3));
		this.local4 = new Token(manager.getStyle(BanjoStyleConstants.LOCAL_4));
		this.local5 = new Token(manager.getStyle(BanjoStyleConstants.LOCAL_5));

		this.function1 = new Token(manager.getStyle(BanjoStyleConstants.LOCAL_1));
		this.function2 = new Token(manager.getStyle(BanjoStyleConstants.LOCAL_2));
		this.function3 = new Token(manager.getStyle(BanjoStyleConstants.LOCAL_3));
		this.function4 = new Token(manager.getStyle(BanjoStyleConstants.LOCAL_4));
		this.function5 = new Token(manager.getStyle(BanjoStyleConstants.LOCAL_5));

		this.parameter1 = new Token(manager.getStyle(BanjoStyleConstants.PARAMETER_1));
		this.parameter2 = new Token(manager.getStyle(BanjoStyleConstants.PARAMETER_2));
		this.parameter3 = new Token(manager.getStyle(BanjoStyleConstants.PARAMETER_3));
		this.parameter4 = new Token(manager.getStyle(BanjoStyleConstants.PARAMETER_4));
		this.parameter5 = new Token(manager.getStyle(BanjoStyleConstants.PARAMETER_5));

		this.field1 = new Token(manager.getStyle(BanjoStyleConstants.FIELD_1));
		this.field2 = new Token(manager.getStyle(BanjoStyleConstants.FIELD_2));
		this.field3 = new Token(manager.getStyle(BanjoStyleConstants.FIELD_3));
		this.field4 = new Token(manager.getStyle(BanjoStyleConstants.FIELD_4));
		this.field5 = new Token(manager.getStyle(BanjoStyleConstants.FIELD_5));

		this.self1 = new Token(manager.getStyle(BanjoStyleConstants.SELF_1));
		this.self2 = new Token(manager.getStyle(BanjoStyleConstants.SELF_2));
		this.self3 = new Token(manager.getStyle(BanjoStyleConstants.SELF_3));
		this.self4 = new Token(manager.getStyle(BanjoStyleConstants.SELF_4));
		this.self5 = new Token(manager.getStyle(BanjoStyleConstants.SELF_5));

	}

	/*
	 * @see ITokenScanner#setRange(IDocument, int, int)
	 */
	@Override
	public void setRange(final IDocument document, int offset, int length) {
		Assert.isLegal(document != null);
		final int documentLength= document.getLength();
		checkRange(offset, length, documentLength);

		setDocument(document);

		this.in = ParserReader.fromSubstring("", document.get(), offset, offset+length);
		final String[] delimiters = document.getLegalLineDelimiters();
		this.legalLineDelimiters = new char[delimiters.length][];
		for (int i= 0; i < delimiters.length; i++)
			this.legalLineDelimiters[i] = delimiters[i].toCharArray();
	}

	public void setDocument(final IDocument document) {
		if(document == this.document)
			return;
		if(this.document != null) {
			this.document.removeDocumentListener(this.documentListener);
			this.ast = null;
		}
		if(document != null) {
			document.addDocumentListener(this.documentListener);
			analyseSource(document);
		}
	}

	static URI EDITOR_FILE_URI_PLACEHOLDER = URI.create("open-file");
	private void analyseSource(IDocument document) {
		try {

			final String body = document.get();
			long startTime = System.currentTimeMillis();
			this.parseResult = this.parser.parse(ParserReader.fromString("", body));
			System.out.println("Took "+(System.currentTimeMillis()-startTime)+" to parse the file.");
			startTime = System.currentTimeMillis();
			this.desugarResult = new BanjoDesugarer(this.parseResult.getSourceMap()).desugar(this.parseResult.getExpr());
			System.out.println("Took "+(System.currentTimeMillis()-startTime)+" to desugar the file.");
			analyse();
		} catch (final IOException e) {
			throw new UnexpectedIOExceptionError(e);
		}
	}

	public void analyse() {
		this.ast = this.desugarResult.getValue();
		final long startTime = System.currentTimeMillis();
		this.defRefAnalysis = new DefRefAnalyser().analyse(EDITOR_FILE_URI_PLACEHOLDER, this.ast, this.parseResult.getFileRange());
		this.sourceRangeAnalysis = this.defRefAnalysis.calculateSourceRanges(this.desugarResult.getDesugarMap(), this.parseResult.getSourceMap());
		System.out.println("Took "+(System.currentTimeMillis()-startTime)+" to analyse the AST.");
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
			return this.scanner.next(this.in, this.tokenVisitor);
		} catch (final IOException e) {
			throw new Error(e); // Shouldn't happen with a string as input
		}
	}

	@Override
	public int getTokenOffset() {
		return this.tokenOffset;
	}

	@Override
	public int getTokenLength() {
		return this.tokenLength;
	}

	private final TokenVisitor<IToken> tokenVisitor = new TokenVisitor<IToken>() {
		IToken token(IToken token, int offset, int length) {
			SourceScanner.this.tokenOffset = offset;
			SourceScanner.this.tokenLength = length;
			if(token != Token.WHITESPACE && token != SourceScanner.this.commentToken)
				SourceScanner.this.inProjection = false;
			return token;
		}

		IToken token(IToken token, FileRange range) {
			return token(token, range.getStartOffset(), range.length());
		}

		IToken token(IToken normalToken, IToken fieldToken, FileRange range) {
			return token(SourceScanner.this.inProjection?fieldToken:normalToken, range);
		}

		@Override
		public IToken whitespace(FileRange range, Whitespace ws) {
			return token(Token.WHITESPACE, range);
		}

		@Override
		public IToken comment(FileRange range, Comment com) {
			return token(SourceScanner.this.commentToken, range);
		}

		@Override
		public IToken eof(FileRange entireFileRange) {
			return token(Token.EOF, SourceScanner.this.tokenOffset+SourceScanner.this.tokenLength, 0);
		}

		@Override
		public IToken operator(FileRange range, OperatorRef or) {
			final IToken result = token(SourceScanner.this.operatorToken, range);
			if(or.getOp().equals(".")) {
				SourceScanner.this.inProjection = true;
			}
			return result;
		}

		@Override
		public IToken stringLiteral(FileRange range, StringLiteral lit) {
			return token(SourceScanner.this.stringLiteralToken, SourceScanner.this.fieldToken, range);
		}

		@Override
		public IToken numberLiteral(FileRange range, NumberLiteral lit) {
			return token(SourceScanner.this.numberLiteralToken, range);
		}

		@Override
		public IToken identifier(FileRange range, Identifier ident) {
			//			final boolean free = SourceScanner.this.sourceRangeAnalysis.getFree().member(range);
			//			final boolean unused = SourceScanner.this.sourceRangeAnalysis.getUnusedDefs().member(range);
			//			final boolean shadowing = SourceScanner.this.sourceRangeAnalysis.getShadowingDefs().member(range);
			//			final boolean ref = SourceScanner.this.sourceRangeAnalysis.getShadowingDefs().member(range);
			//			final DefInfo def =
			//					(SourceScanner.this.defs.containsKey(range.getStartOffset()) ?
			//							SourceScanner.this.defs.get(range.getStartOffset()) :
			//								SourceScanner.this.refs.get(range.getStartOffset()));
			//
			//			if(def != null) {
			//				switch(def.getType()) {
			//				case SELF_FIELD:
			//				case FIELD: {
			//					switch(def.getScopeDepth()%5) {
			//					default:
			//					case 0: return token(SourceScanner.this.field1, range);
			//					case 1: return token(SourceScanner.this.field2, range);
			//					case 2: return token(SourceScanner.this.field3, range);
			//					case 3: return token(SourceScanner.this.field4, range);
			//					case 4: return token(SourceScanner.this.field5, range);
			//					}
			//				}
			//				case LOCAL_FUNCTION: {
			//					switch(def.getScopeDepth()%5) {
			//					default:
			//					case 0: return token(SourceScanner.this.function1, range);
			//					case 1: return token(SourceScanner.this.function2, range);
			//					case 2: return token(SourceScanner.this.function3, range);
			//					case 3: return token(SourceScanner.this.function4, range);
			//					case 4: return token(SourceScanner.this.function5, range);
			//					}
			//				}
			//				case LOCAL_CONST:
			//				case LOCAL_VALUE: {
			//					switch(def.getScopeDepth()%5) {
			//					default:
			//					case 0: return token(SourceScanner.this.local1, range);
			//					case 1: return token(SourceScanner.this.local2, range);
			//					case 2: return token(SourceScanner.this.local3, range);
			//					case 3: return token(SourceScanner.this.local4, range);
			//					case 4: return token(SourceScanner.this.local5, range);
			//					}
			//				}
			//				case PARAMETER: {
			//					switch(def.getScopeDepth()%5) {
			//					default:
			//					case 0: return token(SourceScanner.this.parameter1, range);
			//					case 1: return token(SourceScanner.this.parameter2, range);
			//					case 2: return token(SourceScanner.this.parameter3, range);
			//					case 3: return token(SourceScanner.this.parameter4, range);
			//					case 4: return token(SourceScanner.this.parameter5, range);
			//					}
			//				}
			//				case SELF: {
			//					switch(def.getScopeDepth()%5) {
			//					default:
			//					case 0: return token(SourceScanner.this.self1, range);
			//					case 1: return token(SourceScanner.this.self2, range);
			//					case 2: return token(SourceScanner.this.self3, range);
			//					case 3: return token(SourceScanner.this.self4, range);
			//					case 4: return token(SourceScanner.this.self5, range);
			//					}
			//				}
			//				case SELF_CONST: return token(SourceScanner.this.selfConstToken, range);
			//				case SELF_METHOD: return token(SourceScanner.this.selfMethodToken, range);
			//				}
			//			}
			return token(SourceScanner.this.defaultToken, SourceScanner.this.fieldToken, range);
		}

		@Override
		public IToken ellipsis(FileRange range, Ellipsis ellipsis) {
			return token(SourceScanner.this.identifierToken, range);
		}

		@Override
		public IToken badToken(FileRange range, BadToken badToken) {
			return token(SourceScanner.this.defaultToken, range);
		}
	};

	final IDocumentListener documentListener = new IDocumentListener() {

		@Override
		public void documentChanged(DocumentEvent event) {
			if(event.getDocument() == SourceScanner.this.document) {
				final List<OffsetLength> damageRanges = new ArrayList<>();
				// TODO This should probably be integrated with the DamagerRepairer
				final long startTime = System.currentTimeMillis();
				final P2<ExtSourceExpr, DesugarResult<CoreExpr>> updated = SourceScanner.this.astUpdater.updateAst(
						SourceScanner.this.desugarResult, SourceScanner.this.parseResult,
						event.getOffset(), event.getLength(), event.getText(),
						SourceScanner.this.document.get(), damageRanges);
				SourceScanner.this.desugarResult = updated._2();
				SourceScanner.this.parseResult = updated._1();
				System.out.println("Took "+(System.currentTimeMillis()-startTime)+" to incrementally update the AST.");
				analyse();
			}
		}

		@Override
		public void documentAboutToBeChanged(DocumentEvent event) {	}
	};

}
