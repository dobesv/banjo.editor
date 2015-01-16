package banjo.ui.text;

import java.io.IOException;
import java.io.UncheckedIOException;

import org.eclipse.core.runtime.Assert;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.jface.text.rules.ITokenScanner;
import org.eclipse.jface.text.rules.Token;

import banjo.dom.token.TokenVisitor;
import banjo.parser.SourceCodeScanner;
import banjo.parser.util.FileRange;
import banjo.parser.util.ParserReader;

public class SourceScanner implements ITokenScanner {

	private final class ITokenVisitor implements TokenVisitor<ITokenVisitor> {
		final IToken foundToken;
		public ITokenVisitor() {
			foundToken = null;
		}
		public ITokenVisitor(IToken foundToken) {
			super();
			this.foundToken = foundToken;
		}

		ITokenVisitor token(IToken token, int offset, int length) {
			SourceScanner.this.tokenOffset = offset;
			SourceScanner.this.tokenLength = length;
			if(token != Token.WHITESPACE && token != SourceScanner.this.lineCommentToken)
				SourceScanner.this.inProjection = false;
			return new ITokenVisitor(token);
		}

		ITokenVisitor token(IToken token, FileRange range) {
			return token(token, range.getStartOffset(), range.length());
		}

		ITokenVisitor token(IToken normalToken, IToken fieldToken, FileRange range) {
			return token(SourceScanner.this.inProjection?fieldToken:normalToken, range);
		}

		@Override
		public ITokenVisitor eof(FileRange entireFileRange) {
			return token(Token.EOF, SourceScanner.this.tokenOffset+SourceScanner.this.tokenLength, 0);
		}

		@Override
		public ITokenVisitor stringLiteral(FileRange range, String string) {
			return token(SourceScanner.this.stringLiteralToken, SourceScanner.this.fieldToken, range);
		}

		@Override
		public ITokenVisitor numberLiteral(FileRange range, Number number, String suffix) {
			return token(SourceScanner.this.numberLiteralToken, range);
		}

		@Override
		public ITokenVisitor identifier(FileRange range, String id) {
			return token(SourceScanner.this.defaultToken, SourceScanner.this.fieldToken, range);
		}

		@Override
		public ITokenVisitor operator(FileRange range, String op) {
			final IToken token = op.charAt(0) > 127 ? SourceScanner.this.unicodeOperatorToken : SourceScanner.this.operatorToken;
			final ITokenVisitor result = token(token, range);
			if(op.equals(".")) {
				SourceScanner.this.inProjection = true;
			}
			return result;
		}

		@Override
		public ITokenVisitor whitespace(FileRange range, String text) {
			return token(Token.WHITESPACE, range);
		}

		@Override
		public ITokenVisitor comment(FileRange range, String text) {
			return token(SourceScanner.this.lineCommentToken, range);
		}

		@Override
		public ITokenVisitor badToken(FileRange range, String text, String message) {
			return token(SourceScanner.this.defaultToken, range);
		}
	}

	private IDocument document;

	private char[][] legalLineDelimiters;
	final IToken lineCommentToken;
	final IToken commentToken;
	final IToken unicodeOperatorToken;
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

	private final SourceCodeScanner scanner = new SourceCodeScanner();
	private ParserReader in = null;

	private int tokenOffset;

	private int tokenLength;

	private boolean inProjection;




	public SourceScanner(BanjoStyleManager manager) {
		this.commentToken = new Token(manager.getStyle(BanjoStyleConstants.LINE_COMMENT));
		this.lineCommentToken = new Token(manager.getStyle(BanjoStyleConstants.COMMENT));
		this.unicodeOperatorToken = new Token(manager.getStyle(BanjoStyleConstants.UNICODE_OPERATOR));
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
		this.document = document;
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
			return this.scanner.next(this.in, this.tokenVisitor).foundToken;
		} catch (final IOException e) {
			throw new UncheckedIOException(e); // Shouldn't happen with a string as input
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

	private final ITokenVisitor tokenVisitor = new ITokenVisitor();

}
