package com.atom.chat.chat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuoteParserTest {
    @Test
    void parsesSimpleQuote() {
        QuoteParser.Quote q = QuoteParser.parse("「引用 @Steve: ？？」妈妈");
        assertNotNull(q);
        assertEquals("Steve", q.quoteName());
        assertEquals("？？", q.quoteText());
        assertEquals("妈妈", q.body());
    }

    @Test
    void parsesBodylessQuoteAsEmptyBody() {
        QuoteParser.Quote q = QuoteParser.parse("「引用 @Alice: hello」");
        assertNotNull(q);
        assertEquals("Alice", q.quoteName());
        assertEquals("hello", q.quoteText());
        assertEquals("", q.body());
    }

    @Test
    void preservesTheReplyWhitespaceAfterAQuote() {
        assertEquals("  hello\n😀  ", QuoteParser.parse("「引用 @Alice: hi」  hello\n😀  ").body());
    }

    @Test
    void rejectsMessagesWithoutQuotePrefix() {
        assertNull(QuoteParser.parse("hello"));
        assertNull(QuoteParser.parse("「不是引用 @Alice: hi」"));
        assertNull(QuoteParser.parse(null));
    }

    @Test
    void rejectsMalformedQuotePrefix() {
        assertNull(QuoteParser.parse("「引用 Alice hi」body"));
        assertNull(QuoteParser.parse("「引用 @Alice hi」body"));
        assertNull(QuoteParser.parse("「引用」body"));
    }
}
