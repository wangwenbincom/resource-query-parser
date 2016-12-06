/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.easynet.resource.queryparser;

import java.io.IOException;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.document.DateTools.Resolution;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.MultiPhraseQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.SynonymQuery;
import org.apache.lucene.search.TermQuery;
import org.easynet.resource.queryparser.QueryParser.Operator;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests QueryParser.
 */
public class TestQueryParser extends QueryParserTestBase {

	protected boolean splitOnWhitespace = QueryParser.DEFAULT_SPLIT_ON_WHITESPACE;

	public static class QPTestParser extends QueryParser {
		public QPTestParser(String f, Analyzer a) {
			super(f, a);
		}

		@Override
		protected Query getFuzzyQuery(String field, String termStr, float minSimilarity) throws ParseException {
			throw new ParseException("Fuzzy queries not allowed");
		}

		@Override
		protected Query getWildcardQuery(String field, String termStr) throws ParseException {
			throw new ParseException("Wildcard queries not allowed");
		}
	}

	public QueryParser getParser(Analyzer a) throws Exception {
		if (a == null)
			a = new MockAnalyzer(random(), MockTokenizer.SIMPLE, true);
		QueryParser qp = new QueryParser(getDefaultField(), a);
		qp.setDefaultOperator(QueryParserBase.OR_OPERATOR);
		qp.setSplitOnWhitespace(splitOnWhitespace);
		return qp;
	}

	@Override
	public QueryParser getParserConfig(Analyzer a) throws Exception {
		return getParser(a);
	}

	@Override
	public Query getQuery(String query, QueryParser cqpC) throws Exception {
		assert cqpC != null : "Parameter must not be null";
		assert (cqpC instanceof QueryParser) : "Parameter must be instance of QueryParser";
		QueryParser qp = (QueryParser) cqpC;
		return qp.parse(query);
	}

	@Override
	public Query getQuery(String query, Analyzer a) throws Exception {
		return getParser(a).parse(query);
	}

	@Override
	public boolean isQueryParserException(Exception exception) {
		return exception instanceof ParseException;
	}

	@Override
	public void setDefaultOperatorOR(QueryParser cqpC) {
		assert (cqpC instanceof QueryParser);
		QueryParser qp = (QueryParser) cqpC;
		qp.setDefaultOperator(Operator.OR);
	}

	@Override
	public void setDefaultOperatorAND(QueryParser cqpC) {
		assert (cqpC instanceof QueryParser);
		QueryParser qp = (QueryParser) cqpC;
		qp.setDefaultOperator(Operator.AND);
	}

	@Override
	public void setAnalyzeRangeTerms(QueryParser cqpC, boolean value) {
		assert (cqpC instanceof QueryParser);
		QueryParser qp = (QueryParser) cqpC;
		qp.setAnalyzeRangeTerms(value);
	}

	@Override
	public void setAutoGeneratePhraseQueries(QueryParser cqpC, boolean value) {
		assert (cqpC instanceof QueryParser);
		QueryParser qp = (QueryParser) cqpC;
		qp.setAutoGeneratePhraseQueries(value);
	}

	@Override
	public void setDateResolution(QueryParser cqpC, CharSequence field, Resolution value) {
		assert (cqpC instanceof QueryParser);
		QueryParser qp = (QueryParser) cqpC;
		qp.setDateResolution(field.toString(), value);
	}

	@Override
	public void testDefaultOperator() throws Exception {
		QueryParser qp = getParser(new MockAnalyzer(random()));
		// make sure OR is the default:
		Assert.assertEquals(QueryParserBase.OR_OPERATOR, qp.getDefaultOperator());
		setDefaultOperatorAND(qp);
		Assert.assertEquals(QueryParserBase.AND_OPERATOR, qp.getDefaultOperator());
		setDefaultOperatorOR(qp);
		Assert.assertEquals(QueryParserBase.OR_OPERATOR, qp.getDefaultOperator());
	}

	@Test
	public void testFuzzySlopeExtendability() throws ParseException {
		QueryParser qp = new QueryParser("a", new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false)) {

			@Override
			Query handleBareFuzzy(String qfield, Token fuzzySlop, String termImage) throws ParseException {

				if (fuzzySlop.image.endsWith("€")) {
					float fms = fuzzyMinSim;
					try {
						fms = Float.valueOf(fuzzySlop.image.substring(1, fuzzySlop.image.length() - 1)).floatValue();
					} catch (Exception ignored) {
					}
					float value = Float.parseFloat(termImage);
					return getRangeQuery(qfield, Float.toString(value - fms / 2.f), Float.toString(value + fms / 2.f),
							true, true);
				}
				return super.handleBareFuzzy(qfield, fuzzySlop, termImage);
			}

		};
		Assert.assertEquals(qp.parse("a:[11.95 TO 12.95]"), qp.parse("12.45~1€"));
	}

	@Override
	@Test
	public void testStarParsing() throws Exception {
		final int[] type = new int[1];
		QueryParser qp = new QueryParser("field", new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false)) {
			@Override
			protected Query getWildcardQuery(String field, String termStr) {
				// override error checking of superclass
				type[0] = 1;
				return new TermQuery(new Term(field, termStr));
			}

			@Override
			protected Query getPrefixQuery(String field, String termStr) {
				// override error checking of superclass
				type[0] = 2;
				return new TermQuery(new Term(field, termStr));
			}

			@Override
			protected Query getFieldQuery(String field, String queryText, boolean quoted) throws ParseException {
				type[0] = 3;
				return super.getFieldQuery(field, queryText, quoted);
			}
		};

		TermQuery tq;

		tq = (TermQuery) qp.parse("foo:zoo*");
		Assert.assertEquals("zoo", tq.getTerm().text());
		Assert.assertEquals(2, type[0]);

		BoostQuery bq = (BoostQuery) qp.parse("foo:zoo*^2");
		tq = (TermQuery) bq.getQuery();
		Assert.assertEquals("zoo", tq.getTerm().text());
		Assert.assertEquals(2, type[0]);
		Assert.assertEquals(bq.getBoost(), 2, 0);

		tq = (TermQuery) qp.parse("foo:*");
		Assert.assertEquals("*", tq.getTerm().text());
		Assert.assertEquals(1, type[0]); // could be a valid prefix query in the future
									// too

		bq = (BoostQuery) qp.parse("foo:*^2");
		tq = (TermQuery) bq.getQuery();
		Assert.assertEquals("*", tq.getTerm().text());
		Assert.assertEquals(1, type[0]);
		Assert.assertEquals(bq.getBoost(), 2, 0);

		tq = (TermQuery) qp.parse("*:foo");
		Assert.assertEquals("*", tq.getTerm().field());
		Assert.assertEquals("foo", tq.getTerm().text());
		Assert.assertEquals(3, type[0]);

		tq = (TermQuery) qp.parse("*:*");
		Assert.assertEquals("*", tq.getTerm().field());
		Assert.assertEquals("*", tq.getTerm().text());
		Assert.assertEquals(1, type[0]); // could be handled as a prefix query in the
									// future

		tq = (TermQuery) qp.parse("(*:*)");
		Assert.assertEquals("*", tq.getTerm().field());
		Assert.assertEquals("*", tq.getTerm().text());
		Assert.assertEquals(1, type[0]);

	}

	/**
	 * query parser that doesn't expand synonyms when users use double quotes
	 */
	private class SmartQueryParser extends QueryParser {
		Analyzer morePrecise = new Analyzer2();

		public SmartQueryParser() {
			super("field", new Analyzer1());
		}

		@Override
		protected Query getFieldQuery(String field, String queryText, boolean quoted) throws ParseException {
			if (quoted)
				return newFieldQuery(morePrecise, field, queryText, quoted);
			else
				return super.getFieldQuery(field, queryText, quoted);
		}
	}

	@Override
	@Test
	public void testNewFieldQuery() throws Exception {
		/** ordinary behavior, synonyms form uncoordinated boolean query */
		QueryParser dumb = new QueryParser("field", new Analyzer1());
		Query expanded = new SynonymQuery(new Term("field", "dogs"), new Term("field", "dog"));
		Assert.assertEquals(expanded, dumb.parse("\"dogs\""));
		/** even with the phrase operator the behavior is the same */
		Assert.assertEquals(expanded, dumb.parse("dogs"));

		/**
		 * custom behavior, the synonyms are expanded, unless you use quote
		 * operator
		 */
		QueryParser smart = new SmartQueryParser();
		Assert.assertEquals(expanded, smart.parse("dogs"));

		Query unexpanded = new TermQuery(new Term("field", "dogs"));
		Assert.assertEquals(unexpanded, smart.parse("\"dogs\""));
	}

	/** simple synonyms test */
	@Test
	public void testSynonyms() throws Exception {
		Query expected = new SynonymQuery(new Term("field", "dogs"), new Term("field", "dog"));
		QueryParser qp = new QueryParser("field", new MockSynonymAnalyzer());
		Assert.assertEquals(expected, qp.parse("dogs"));
		Assert.assertEquals(expected, qp.parse("\"dogs\""));
		qp.setDefaultOperator(Operator.AND);
		Assert.assertEquals(expected, qp.parse("dogs"));
		Assert.assertEquals(expected, qp.parse("\"dogs\""));
		expected = new BoostQuery(expected, 2f);
		Assert.assertEquals(expected, qp.parse("dogs^2"));
		Assert.assertEquals(expected, qp.parse("\"dogs\"^2"));
	}

	/** forms multiphrase query */
	@Test
	public void testSynonymsPhrase() throws Exception {
		MultiPhraseQuery.Builder expectedQBuilder = new MultiPhraseQuery.Builder();
		expectedQBuilder.add(new Term("field", "old"));
		expectedQBuilder.add(new Term[] { new Term("field", "dogs"), new Term("field", "dog") });
		QueryParser qp = new QueryParser("field", new MockSynonymAnalyzer());
		Assert.assertEquals(expectedQBuilder.build(), qp.parse("\"old dogs\""));
		qp.setDefaultOperator(Operator.AND);
		Assert.assertEquals(expectedQBuilder.build(), qp.parse("\"old dogs\""));
		BoostQuery expected = new BoostQuery(expectedQBuilder.build(), 2f);
		Assert.assertEquals(expected, qp.parse("\"old dogs\"^2"));
		expectedQBuilder.setSlop(3);
		expected = new BoostQuery(expectedQBuilder.build(), 2f);
		Assert.assertEquals(expected, qp.parse("\"old dogs\"~3^2"));
	}

	/**
	 * adds synonym of "國" for "国".
	 */
	protected static class MockCJKSynonymFilter extends TokenFilter {
		CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
		PositionIncrementAttribute posIncAtt = addAttribute(PositionIncrementAttribute.class);
		boolean addSynonym = false;

		public MockCJKSynonymFilter(TokenStream input) {
			super(input);
		}

		@Override
		public final boolean incrementToken() throws IOException {
			if (addSynonym) { // inject our synonym
				clearAttributes();
				termAtt.setEmpty().append("國");
				posIncAtt.setPositionIncrement(0);
				addSynonym = false;
				return true;
			}

			if (input.incrementToken()) {
				addSynonym = termAtt.toString().equals("国");
				return true;
			} else {
				return false;
			}
		}
	}

	static class MockCJKSynonymAnalyzer extends Analyzer {
		@Override
		protected TokenStreamComponents createComponents(String fieldName) {
			Tokenizer tokenizer = new SimpleCJKTokenizer();
			return new TokenStreamComponents(tokenizer, new MockCJKSynonymFilter(tokenizer));
		}
	}

	/** simple CJK synonym test */
	@Test
	public void testCJKSynonym() throws Exception {
		Query expected = new SynonymQuery(new Term("field", "国"), new Term("field", "國"));
		QueryParser qp = new QueryParser("field", new MockCJKSynonymAnalyzer());
		Assert.assertEquals(expected, qp.parse("国"));
		qp.setDefaultOperator(Operator.AND);
		Assert.assertEquals(expected, qp.parse("国"));
		expected = new BoostQuery(expected, 2f);
		Assert.assertEquals(expected, qp.parse("国^2"));
	}

	/** synonyms with default OR operator */
	@Test
	public void testCJKSynonymsOR() throws Exception {
		BooleanQuery.Builder expectedB = new BooleanQuery.Builder();
		expectedB.add(new TermQuery(new Term("field", "中")), BooleanClause.Occur.SHOULD);
		Query inner = new SynonymQuery(new Term("field", "国"), new Term("field", "國"));
		expectedB.add(inner, BooleanClause.Occur.SHOULD);
		Query expected = expectedB.build();
		QueryParser qp = new QueryParser("field", new MockCJKSynonymAnalyzer());
		Assert.assertEquals(expected, qp.parse("中国"));
		expected = new BoostQuery(expected, 2f);
		Assert.assertEquals(expected, qp.parse("中国^2"));
	}

	/** more complex synonyms with default OR operator */
	@Test
	public void testCJKSynonymsOR2() throws Exception {
		BooleanQuery.Builder expectedB = new BooleanQuery.Builder();
		expectedB.add(new TermQuery(new Term("field", "中")), BooleanClause.Occur.SHOULD);
		SynonymQuery inner = new SynonymQuery(new Term("field", "国"), new Term("field", "國"));
		expectedB.add(inner, BooleanClause.Occur.SHOULD);
		SynonymQuery inner2 = new SynonymQuery(new Term("field", "国"), new Term("field", "國"));
		expectedB.add(inner2, BooleanClause.Occur.SHOULD);
		Query expected = expectedB.build();
		QueryParser qp = new QueryParser("field", new MockCJKSynonymAnalyzer());
		Assert.assertEquals(expected, qp.parse("中国国"));
		expected = new BoostQuery(expected, 2f);
		Assert.assertEquals(expected, qp.parse("中国国^2"));
	}

	/** synonyms with default AND operator */
	@Test
	public void testCJKSynonymsAND() throws Exception {
		BooleanQuery.Builder expectedB = new BooleanQuery.Builder();
		expectedB.add(new TermQuery(new Term("field", "中")), BooleanClause.Occur.MUST);
		Query inner = new SynonymQuery(new Term("field", "国"), new Term("field", "國"));
		expectedB.add(inner, BooleanClause.Occur.MUST);
		Query expected = expectedB.build();
		QueryParser qp = new QueryParser("field", new MockCJKSynonymAnalyzer());
		qp.setDefaultOperator(Operator.AND);
		Assert.assertEquals(expected, qp.parse("中国"));
		expected = new BoostQuery(expected, 2f);
		Assert.assertEquals(expected, qp.parse("中国^2"));
	}

	/** more complex synonyms with default AND operator */
	@Test
	public void testCJKSynonymsAND2() throws Exception {
		BooleanQuery.Builder expectedB = new BooleanQuery.Builder();
		expectedB.add(new TermQuery(new Term("field", "中")), BooleanClause.Occur.MUST);
		Query inner = new SynonymQuery(new Term("field", "国"), new Term("field", "國"));
		expectedB.add(inner, BooleanClause.Occur.MUST);
		Query inner2 = new SynonymQuery(new Term("field", "国"), new Term("field", "國"));
		expectedB.add(inner2, BooleanClause.Occur.MUST);
		Query expected = expectedB.build();
		QueryParser qp = new QueryParser("field", new MockCJKSynonymAnalyzer());
		qp.setDefaultOperator(Operator.AND);
		Assert.assertEquals(expected, qp.parse("中国国"));
		expected = new BoostQuery(expected, 2f);
		Assert.assertEquals(expected, qp.parse("中国国^2"));
	}

	/** forms multiphrase query */
	@Test
	public void testCJKSynonymsPhrase() throws Exception {
		MultiPhraseQuery.Builder expectedQBuilder = new MultiPhraseQuery.Builder();
		expectedQBuilder.add(new Term("field", "中"));
		expectedQBuilder.add(new Term[] { new Term("field", "国"), new Term("field", "國") });
		QueryParser qp = new QueryParser("field", new MockCJKSynonymAnalyzer());
		qp.setDefaultOperator(Operator.AND);
		Assert.assertEquals(expectedQBuilder.build(), qp.parse("\"中国\""));
		Query expected = new BoostQuery(expectedQBuilder.build(), 2f);
		Assert.assertEquals(expected, qp.parse("\"中国\"^2"));
		expectedQBuilder.setSlop(3);
		expected = new BoostQuery(expectedQBuilder.build(), 2f);
		Assert.assertEquals(expected, qp.parse("\"中国\"~3^2"));
	}


	// TODO: Remove this specialization once the flexible standard parser gets
	// multi-word synonym support
	@Override
	@Test
	public void testQPA() throws Exception {
		boolean oldSplitOnWhitespace = splitOnWhitespace;
		splitOnWhitespace = false;

		assertQueryEquals("term phrase term", qpAnalyzer, "term phrase1 phrase2 term");

		QueryParser cqpc = getParserConfig(qpAnalyzer);
		setDefaultOperatorAND(cqpc);
		assertQueryEquals(cqpc, "field", "term phrase term", "+term +phrase1 +phrase2 +term");

		splitOnWhitespace = oldSplitOnWhitespace;
	}

	// TODO: Move to QueryParserTestBase once standard flexible parser gets this
	// capability
	@Test
	public void testMultiWordSynonyms() throws Exception {
		QueryParser dumb = new QueryParser("field", new Analyzer1());
		dumb.setSplitOnWhitespace(false);

		// A multi-word synonym source will form a synonym query for the
		// same-starting-position tokens
		BooleanQuery.Builder multiWordExpandedBqBuilder = new BooleanQuery.Builder();
		Query multiWordSynonymQuery = new SynonymQuery(new Term("field", "guinea"), new Term("field", "cavy"));
		multiWordExpandedBqBuilder.add(multiWordSynonymQuery, BooleanClause.Occur.SHOULD);
		multiWordExpandedBqBuilder.add(new TermQuery(new Term("field", "pig")), BooleanClause.Occur.SHOULD);
		Query multiWordExpandedBq = multiWordExpandedBqBuilder.build();
		Assert.assertEquals(multiWordExpandedBq, dumb.parse("guinea pig"));

		// With the phrase operator, a multi-word synonym source will form a
		// multiphrase query.
		// When the number of expanded term(s) is different from that of the
		// original term(s), this is not good.
		MultiPhraseQuery.Builder multiWordExpandedMpqBuilder = new MultiPhraseQuery.Builder();
		multiWordExpandedMpqBuilder.add(new Term[] { new Term("field", "guinea"), new Term("field", "cavy") });
		multiWordExpandedMpqBuilder.add(new Term("field", "pig"));
		Query multiWordExpandedMPQ = multiWordExpandedMpqBuilder.build();
		Assert.assertEquals(multiWordExpandedMPQ, dumb.parse("\"guinea pig\""));

		// custom behavior, the synonyms are expanded, unless you use quote
		// operator
		QueryParser smart = new SmartQueryParser();
		smart.setSplitOnWhitespace(false);
		Assert.assertEquals(multiWordExpandedBq, smart.parse("guinea pig"));

		PhraseQuery.Builder multiWordUnexpandedPqBuilder = new PhraseQuery.Builder();
		multiWordUnexpandedPqBuilder.add(new Term("field", "guinea"));
		multiWordUnexpandedPqBuilder.add(new Term("field", "pig"));
		Query multiWordUnexpandedPq = multiWordUnexpandedPqBuilder.build();
		Assert.assertEquals(multiWordUnexpandedPq, smart.parse("\"guinea pig\""));
	}

	// TODO: Move to QueryParserTestBase once standard flexible parser gets this
	// capability
	@Test
	public void testOperatorsAndMultiWordSynonyms() throws Exception {
		Analyzer a = new MockSynonymAnalyzer();

		boolean oldSplitOnWhitespace = splitOnWhitespace;
		splitOnWhitespace = false;

		// Operators should interrupt multiword analysis of adjacent words if
		// they associate
		assertQueryEquals("+guinea pig", a, "+guinea pig");
		assertQueryEquals("-guinea pig", a, "-guinea pig");
		assertQueryEquals("!guinea pig", a, "-guinea pig");
		assertQueryEquals("guinea* pig", a, "guinea* pig");
		assertQueryEquals("guinea? pig", a, "guinea? pig");
		assertQueryEquals("guinea~2 pig", a, "guinea~2 pig");
		assertQueryEquals("guinea^2 pig", a, "(guinea)^2.0 pig");

		assertQueryEquals("guinea +pig", a, "guinea +pig");
		assertQueryEquals("guinea -pig", a, "guinea -pig");
		assertQueryEquals("guinea !pig", a, "guinea -pig");
		assertQueryEquals("guinea pig*", a, "guinea pig*");
		assertQueryEquals("guinea pig?", a, "guinea pig?");
		assertQueryEquals("guinea pig~2", a, "guinea pig~2");
		assertQueryEquals("guinea pig^2", a, "guinea (pig)^2.0");

		assertQueryEquals("field:guinea pig", a, "guinea pig");
		assertQueryEquals("guinea field:pig", a, "guinea pig");

		assertQueryEquals("NOT guinea pig", a, "-guinea pig");
		assertQueryEquals("guinea NOT pig", a, "guinea -pig");

		assertQueryEquals("guinea pig AND dogs", a, "guinea +pig +Synonym(dog dogs)");
		assertQueryEquals("dogs AND guinea pig", a, "+Synonym(dog dogs) +guinea pig");
		assertQueryEquals("guinea pig && dogs", a, "guinea +pig +Synonym(dog dogs)");
		assertQueryEquals("dogs && guinea pig", a, "+Synonym(dog dogs) +guinea pig");

		assertQueryEquals("guinea pig OR dogs", a, "guinea pig Synonym(dog dogs)");
		assertQueryEquals("dogs OR guinea pig", a, "Synonym(dog dogs) guinea pig");
		assertQueryEquals("guinea pig || dogs", a, "guinea pig Synonym(dog dogs)");
		assertQueryEquals("dogs || guinea pig", a, "Synonym(dog dogs) guinea pig");

		assertQueryEquals("\"guinea\" pig", a, "guinea pig");
		assertQueryEquals("guinea \"pig\"", a, "guinea pig");

		assertQueryEquals("(guinea) pig", a, "guinea pig");
		assertQueryEquals("guinea (pig)", a, "guinea pig");

		assertQueryEquals("/guinea/ pig", a, "/guinea/ pig");
		assertQueryEquals("guinea /pig/", a, "guinea /pig/");

		// Operators should not interrupt multiword analysis if not don't
		// associate
		assertQueryEquals("(guinea pig)", a, "Synonym(cavy guinea) pig");
		assertQueryEquals("+(guinea pig)", a, "+(Synonym(cavy guinea) pig)");
		assertQueryEquals("-(guinea pig)", a, "-(Synonym(cavy guinea) pig)");
		assertQueryEquals("!(guinea pig)", a, "-(Synonym(cavy guinea) pig)");
		assertQueryEquals("NOT (guinea pig)", a, "-(Synonym(cavy guinea) pig)");
		assertQueryEquals("(guinea pig)^2", a, "(Synonym(cavy guinea) pig)^2.0");

		assertQueryEquals("field:(guinea pig)", a, "Synonym(cavy guinea) pig");

		assertQueryEquals("+small guinea pig", a, "+small Synonym(cavy guinea) pig");
		assertQueryEquals("-small guinea pig", a, "-small Synonym(cavy guinea) pig");
		assertQueryEquals("!small guinea pig", a, "-small Synonym(cavy guinea) pig");
		assertQueryEquals("NOT small guinea pig", a, "-small Synonym(cavy guinea) pig");
		assertQueryEquals("small* guinea pig", a, "small* Synonym(cavy guinea) pig");
		assertQueryEquals("small? guinea pig", a, "small? Synonym(cavy guinea) pig");
		assertQueryEquals("\"small\" guinea pig", a, "small Synonym(cavy guinea) pig");

		assertQueryEquals("guinea pig +running", a, "Synonym(cavy guinea) pig +running");
		assertQueryEquals("guinea pig -running", a, "Synonym(cavy guinea) pig -running");
		assertQueryEquals("guinea pig !running", a, "Synonym(cavy guinea) pig -running");
		assertQueryEquals("guinea pig NOT running", a, "Synonym(cavy guinea) pig -running");
		assertQueryEquals("guinea pig running*", a, "Synonym(cavy guinea) pig running*");
		assertQueryEquals("guinea pig running?", a, "Synonym(cavy guinea) pig running?");
		assertQueryEquals("guinea pig \"running\"", a, "Synonym(cavy guinea) pig running");

		assertQueryEquals("\"guinea pig\"~2", a, "\"(guinea cavy) pig\"~2");

		assertQueryEquals("field:\"guinea pig\"", a, "\"(guinea cavy) pig\"");

		splitOnWhitespace = oldSplitOnWhitespace;
	}

	@Test
	public void testOperatorsAndMultiWordSynonymsSplitOnWhitespace() throws Exception {
		Analyzer a = new MockSynonymAnalyzer();

		boolean oldSplitOnWhitespace = splitOnWhitespace;
		splitOnWhitespace = true;

		assertQueryEquals("+guinea pig", a, "+guinea pig");
		assertQueryEquals("-guinea pig", a, "-guinea pig");
		assertQueryEquals("!guinea pig", a, "-guinea pig");
		assertQueryEquals("guinea* pig", a, "guinea* pig");
		assertQueryEquals("guinea? pig", a, "guinea? pig");
		assertQueryEquals("guinea~2 pig", a, "guinea~2 pig");
		assertQueryEquals("guinea^2 pig", a, "(guinea)^2.0 pig");

		assertQueryEquals("guinea +pig", a, "guinea +pig");
		assertQueryEquals("guinea -pig", a, "guinea -pig");
		assertQueryEquals("guinea !pig", a, "guinea -pig");
		assertQueryEquals("guinea pig*", a, "guinea pig*");
		assertQueryEquals("guinea pig?", a, "guinea pig?");
		assertQueryEquals("guinea pig~2", a, "guinea pig~2");
		assertQueryEquals("guinea pig^2", a, "guinea (pig)^2.0");

		assertQueryEquals("field:guinea pig", a, "guinea pig");
		assertQueryEquals("guinea field:pig", a, "guinea pig");

		assertQueryEquals("NOT guinea pig", a, "-guinea pig");
		assertQueryEquals("guinea NOT pig", a, "guinea -pig");

		assertQueryEquals("guinea pig AND dogs", a, "guinea +pig +Synonym(dog dogs)");
		assertQueryEquals("dogs AND guinea pig", a, "+Synonym(dog dogs) +guinea pig");
		assertQueryEquals("guinea pig && dogs", a, "guinea +pig +Synonym(dog dogs)");
		assertQueryEquals("dogs && guinea pig", a, "+Synonym(dog dogs) +guinea pig");

		assertQueryEquals("guinea pig OR dogs", a, "guinea pig Synonym(dog dogs)");
		assertQueryEquals("dogs OR guinea pig", a, "Synonym(dog dogs) guinea pig");
		assertQueryEquals("guinea pig || dogs", a, "guinea pig Synonym(dog dogs)");
		assertQueryEquals("dogs || guinea pig", a, "Synonym(dog dogs) guinea pig");

		assertQueryEquals("\"guinea\" pig", a, "guinea pig");
		assertQueryEquals("guinea \"pig\"", a, "guinea pig");

		assertQueryEquals("(guinea) pig", a, "guinea pig");
		assertQueryEquals("guinea (pig)", a, "guinea pig");

		assertQueryEquals("/guinea/ pig", a, "/guinea/ pig");
		assertQueryEquals("guinea /pig/", a, "guinea /pig/");

		assertQueryEquals("(guinea pig)", a, "guinea pig");
		assertQueryEquals("+(guinea pig)", a, "+(guinea pig)");
		assertQueryEquals("-(guinea pig)", a, "-(guinea pig)");
		assertQueryEquals("!(guinea pig)", a, "-(guinea pig)");
		assertQueryEquals("NOT (guinea pig)", a, "-(guinea pig)");
		assertQueryEquals("(guinea pig)^2", a, "(guinea pig)^2.0");

		assertQueryEquals("field:(guinea pig)", a, "guinea pig");

		assertQueryEquals("+small guinea pig", a, "+small guinea pig");
		assertQueryEquals("-small guinea pig", a, "-small guinea pig");
		assertQueryEquals("!small guinea pig", a, "-small guinea pig");
		assertQueryEquals("NOT small guinea pig", a, "-small guinea pig");
		assertQueryEquals("small* guinea pig", a, "small* guinea pig");
		assertQueryEquals("small? guinea pig", a, "small? guinea pig");
		assertQueryEquals("\"small\" guinea pig", a, "small guinea pig");

		assertQueryEquals("guinea pig +running", a, "guinea pig +running");
		assertQueryEquals("guinea pig -running", a, "guinea pig -running");
		assertQueryEquals("guinea pig !running", a, "guinea pig -running");
		assertQueryEquals("guinea pig NOT running", a, "guinea pig -running");
		assertQueryEquals("guinea pig running*", a, "guinea pig running*");
		assertQueryEquals("guinea pig running?", a, "guinea pig running?");
		assertQueryEquals("guinea pig \"running\"", a, "guinea pig running");

		assertQueryEquals("\"guinea pig\"~2", a, "\"(guinea cavy) pig\"~2");

		assertQueryEquals("field:\"guinea pig\"", a, "\"(guinea cavy) pig\"");

		splitOnWhitespace = oldSplitOnWhitespace;
	}

	@Test
	public void testDefaultSplitOnWhitespace() throws Exception {
		QueryParser parser = new QueryParser("field", new Analyzer1());

		Assert.assertTrue(parser.getSplitOnWhitespace()); // default is true

		BooleanQuery.Builder bqBuilder = new BooleanQuery.Builder();
		bqBuilder.add(new TermQuery(new Term("field", "guinea")), BooleanClause.Occur.SHOULD);
		bqBuilder.add(new TermQuery(new Term("field", "pig")), BooleanClause.Occur.SHOULD);
		Assert.assertEquals(bqBuilder.build(), parser.parse("guinea pig"));

		boolean oldSplitOnWhitespace = splitOnWhitespace;
		splitOnWhitespace = QueryParser.DEFAULT_SPLIT_ON_WHITESPACE;
		assertQueryEquals("guinea pig", new MockSynonymAnalyzer(), "guinea pig");
		splitOnWhitespace = oldSplitOnWhitespace;
	}
}