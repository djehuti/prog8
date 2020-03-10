// Generated from prog8.g4 by ANTLR 4.8

package prog8.parser;

import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.misc.*;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast"})
public class prog8Lexer extends Lexer {
	static { RuntimeMetaData.checkVersion("4.8", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		T__0=1, T__1=2, T__2=3, T__3=4, T__4=5, T__5=6, T__6=7, T__7=8, T__8=9, 
		T__9=10, T__10=11, T__11=12, T__12=13, T__13=14, T__14=15, T__15=16, T__16=17, 
		T__17=18, T__18=19, T__19=20, T__20=21, T__21=22, T__22=23, T__23=24, 
		T__24=25, T__25=26, T__26=27, T__27=28, T__28=29, T__29=30, T__30=31, 
		T__31=32, T__32=33, T__33=34, T__34=35, T__35=36, T__36=37, T__37=38, 
		T__38=39, T__39=40, T__40=41, T__41=42, T__42=43, T__43=44, T__44=45, 
		T__45=46, T__46=47, T__47=48, T__48=49, T__49=50, T__50=51, T__51=52, 
		T__52=53, T__53=54, T__54=55, T__55=56, T__56=57, T__57=58, T__58=59, 
		T__59=60, T__60=61, T__61=62, T__62=63, T__63=64, T__64=65, T__65=66, 
		T__66=67, T__67=68, T__68=69, T__69=70, T__70=71, T__71=72, T__72=73, 
		T__73=74, T__74=75, T__75=76, T__76=77, T__77=78, T__78=79, T__79=80, 
		T__80=81, T__81=82, T__82=83, T__83=84, T__84=85, T__85=86, T__86=87, 
		T__87=88, T__88=89, T__89=90, T__90=91, T__91=92, T__92=93, T__93=94, 
		T__94=95, T__95=96, T__96=97, T__97=98, T__98=99, T__99=100, T__100=101, 
		T__101=102, T__102=103, T__103=104, T__104=105, T__105=106, T__106=107, 
		T__107=108, T__108=109, T__109=110, LINECOMMENT=111, COMMENT=112, WS=113, 
		EOL=114, VOID=115, NAME=116, DEC_INTEGER=117, HEX_INTEGER=118, BIN_INTEGER=119, 
		ADDRESS_OF=120, FLOAT_NUMBER=121, STRING=122, INLINEASMBLOCK=123, SINGLECHAR=124, 
		ZEROPAGE=125, ARRAYSIG=126;
	public static String[] channelNames = {
		"DEFAULT_TOKEN_CHANNEL", "HIDDEN"
	};

	public static String[] modeNames = {
		"DEFAULT_MODE"
	};

	private static String[] makeRuleNames() {
		return new String[] {
			"T__0", "T__1", "T__2", "T__3", "T__4", "T__5", "T__6", "T__7", "T__8", 
			"T__9", "T__10", "T__11", "T__12", "T__13", "T__14", "T__15", "T__16", 
			"T__17", "T__18", "T__19", "T__20", "T__21", "T__22", "T__23", "T__24", 
			"T__25", "T__26", "T__27", "T__28", "T__29", "T__30", "T__31", "T__32", 
			"T__33", "T__34", "T__35", "T__36", "T__37", "T__38", "T__39", "T__40", 
			"T__41", "T__42", "T__43", "T__44", "T__45", "T__46", "T__47", "T__48", 
			"T__49", "T__50", "T__51", "T__52", "T__53", "T__54", "T__55", "T__56", 
			"T__57", "T__58", "T__59", "T__60", "T__61", "T__62", "T__63", "T__64", 
			"T__65", "T__66", "T__67", "T__68", "T__69", "T__70", "T__71", "T__72", 
			"T__73", "T__74", "T__75", "T__76", "T__77", "T__78", "T__79", "T__80", 
			"T__81", "T__82", "T__83", "T__84", "T__85", "T__86", "T__87", "T__88", 
			"T__89", "T__90", "T__91", "T__92", "T__93", "T__94", "T__95", "T__96", 
			"T__97", "T__98", "T__99", "T__100", "T__101", "T__102", "T__103", "T__104", 
			"T__105", "T__106", "T__107", "T__108", "T__109", "LINECOMMENT", "COMMENT", 
			"WS", "EOL", "VOID", "NAME", "DEC_INTEGER", "HEX_INTEGER", "BIN_INTEGER", 
			"ADDRESS_OF", "FLOAT_NUMBER", "FNUMBER", "STRING_ESCAPE_SEQ", "STRING", 
			"INLINEASMBLOCK", "SINGLECHAR", "ZEROPAGE", "ARRAYSIG"
		};
	}
	public static final String[] ruleNames = makeRuleNames();

	private static String[] makeLiteralNames() {
		return new String[] {
			null, "':'", "'goto'", "'%output'", "'%launcher'", "'%zeropage'", "'%zpreserved'", 
			"'%address'", "'%import'", "'%breakpoint'", "'%asminclude'", "'%asmbinary'", 
			"'%option'", "','", "'='", "'const'", "'struct'", "'{'", "'}'", "'ubyte'", 
			"'byte'", "'uword'", "'word'", "'float'", "'str'", "'['", "']'", "'+='", 
			"'-='", "'/='", "'*='", "'**='", "'&='", "'|='", "'^='", "'%='", "'<<='", 
			"'>>='", "'++'", "'--'", "'+'", "'-'", "'~'", "'**'", "'*'", "'/'", "'%'", 
			"'<<'", "'>>'", "'<'", "'>'", "'<='", "'>='", "'=='", "'!='", "'^'", 
			"'|'", "'to'", "'step'", "'and'", "'or'", "'xor'", "'not'", "'('", "')'", 
			"'as'", "'@'", "'return'", "'break'", "'continue'", "'.'", "'A'", "'X'", 
			"'Y'", "'AX'", "'AY'", "'XY'", "'Pc'", "'Pz'", "'Pn'", "'Pv'", "'.w'", 
			"'true'", "'false'", "'%asm'", "'sub'", "'->'", "'asmsub'", "'romsub'", 
			"'stack'", "'clobbers'", "'if'", "'else'", "'if_cs'", "'if_cc'", "'if_eq'", 
			"'if_z'", "'if_ne'", "'if_nz'", "'if_pl'", "'if_pos'", "'if_mi'", "'if_neg'", 
			"'if_vs'", "'if_vc'", "'for'", "'in'", "'while'", "'repeat'", "'until'", 
			"'when'", null, null, null, null, "'void'", null, null, null, null, "'&'", 
			null, null, null, null, "'@zp'", "'[]'"
		};
	}
	private static final String[] _LITERAL_NAMES = makeLiteralNames();
	private static String[] makeSymbolicNames() {
		return new String[] {
			null, null, null, null, null, null, null, null, null, null, null, null, 
			null, null, null, null, null, null, null, null, null, null, null, null, 
			null, null, null, null, null, null, null, null, null, null, null, null, 
			null, null, null, null, null, null, null, null, null, null, null, null, 
			null, null, null, null, null, null, null, null, null, null, null, null, 
			null, null, null, null, null, null, null, null, null, null, null, null, 
			null, null, null, null, null, null, null, null, null, null, null, null, 
			null, null, null, null, null, null, null, null, null, null, null, null, 
			null, null, null, null, null, null, null, null, null, null, null, null, 
			null, null, null, "LINECOMMENT", "COMMENT", "WS", "EOL", "VOID", "NAME", 
			"DEC_INTEGER", "HEX_INTEGER", "BIN_INTEGER", "ADDRESS_OF", "FLOAT_NUMBER", 
			"STRING", "INLINEASMBLOCK", "SINGLECHAR", "ZEROPAGE", "ARRAYSIG"
		};
	}
	private static final String[] _SYMBOLIC_NAMES = makeSymbolicNames();
	public static final Vocabulary VOCABULARY = new VocabularyImpl(_LITERAL_NAMES, _SYMBOLIC_NAMES);

	/**
	 * @deprecated Use {@link #VOCABULARY} instead.
	 */
	@Deprecated
	public static final String[] tokenNames;
	static {
		tokenNames = new String[_SYMBOLIC_NAMES.length];
		for (int i = 0; i < tokenNames.length; i++) {
			tokenNames[i] = VOCABULARY.getLiteralName(i);
			if (tokenNames[i] == null) {
				tokenNames[i] = VOCABULARY.getSymbolicName(i);
			}

			if (tokenNames[i] == null) {
				tokenNames[i] = "<INVALID>";
			}
		}
	}

	@Override
	@Deprecated
	public String[] getTokenNames() {
		return tokenNames;
	}

	@Override

	public Vocabulary getVocabulary() {
		return VOCABULARY;
	}


	public prog8Lexer(CharStream input) {
		super(input);
		_interp = new LexerATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}

	@Override
	public String getGrammarFileName() { return "prog8.g4"; }

	@Override
	public String[] getRuleNames() { return ruleNames; }

	@Override
	public String getSerializedATN() { return _serializedATN; }

	@Override
	public String[] getChannelNames() { return channelNames; }

	@Override
	public String[] getModeNames() { return modeNames; }

	@Override
	public ATN getATN() { return _ATN; }

	@Override
	public void action(RuleContext _localctx, int ruleIndex, int actionIndex) {
		switch (ruleIndex) {
		case 123:
			STRING_action((RuleContext)_localctx, actionIndex);
			break;
		case 124:
			INLINEASMBLOCK_action((RuleContext)_localctx, actionIndex);
			break;
		case 125:
			SINGLECHAR_action((RuleContext)_localctx, actionIndex);
			break;
		}
	}
	private void STRING_action(RuleContext _localctx, int actionIndex) {
		switch (actionIndex) {
		case 0:

					// get rid of the enclosing quotes
					String s = getText();
					setText(s.substring(1, s.length() - 1));
				
			break;
		}
	}
	private void INLINEASMBLOCK_action(RuleContext _localctx, int actionIndex) {
		switch (actionIndex) {
		case 1:

					// get rid of the enclosing double braces
					String s = getText();
					setText(s.substring(2, s.length() - 2));
				
			break;
		}
	}
	private void SINGLECHAR_action(RuleContext _localctx, int actionIndex) {
		switch (actionIndex) {
		case 2:

					// get rid of the enclosing quotes
					String s = getText();
					setText(s.substring(1, s.length() - 1));
				
			break;
		}
	}

	public static final String _serializedATN =
		"\3\u608b\ua72a\u8133\ub9ed\u417c\u3be7\u7786\u5964\2\u0080\u0376\b\1\4"+
		"\2\t\2\4\3\t\3\4\4\t\4\4\5\t\5\4\6\t\6\4\7\t\7\4\b\t\b\4\t\t\t\4\n\t\n"+
		"\4\13\t\13\4\f\t\f\4\r\t\r\4\16\t\16\4\17\t\17\4\20\t\20\4\21\t\21\4\22"+
		"\t\22\4\23\t\23\4\24\t\24\4\25\t\25\4\26\t\26\4\27\t\27\4\30\t\30\4\31"+
		"\t\31\4\32\t\32\4\33\t\33\4\34\t\34\4\35\t\35\4\36\t\36\4\37\t\37\4 \t"+
		" \4!\t!\4\"\t\"\4#\t#\4$\t$\4%\t%\4&\t&\4\'\t\'\4(\t(\4)\t)\4*\t*\4+\t"+
		"+\4,\t,\4-\t-\4.\t.\4/\t/\4\60\t\60\4\61\t\61\4\62\t\62\4\63\t\63\4\64"+
		"\t\64\4\65\t\65\4\66\t\66\4\67\t\67\48\t8\49\t9\4:\t:\4;\t;\4<\t<\4=\t"+
		"=\4>\t>\4?\t?\4@\t@\4A\tA\4B\tB\4C\tC\4D\tD\4E\tE\4F\tF\4G\tG\4H\tH\4"+
		"I\tI\4J\tJ\4K\tK\4L\tL\4M\tM\4N\tN\4O\tO\4P\tP\4Q\tQ\4R\tR\4S\tS\4T\t"+
		"T\4U\tU\4V\tV\4W\tW\4X\tX\4Y\tY\4Z\tZ\4[\t[\4\\\t\\\4]\t]\4^\t^\4_\t_"+
		"\4`\t`\4a\ta\4b\tb\4c\tc\4d\td\4e\te\4f\tf\4g\tg\4h\th\4i\ti\4j\tj\4k"+
		"\tk\4l\tl\4m\tm\4n\tn\4o\to\4p\tp\4q\tq\4r\tr\4s\ts\4t\tt\4u\tu\4v\tv"+
		"\4w\tw\4x\tx\4y\ty\4z\tz\4{\t{\4|\t|\4}\t}\4~\t~\4\177\t\177\4\u0080\t"+
		"\u0080\4\u0081\t\u0081\3\2\3\2\3\3\3\3\3\3\3\3\3\3\3\4\3\4\3\4\3\4\3\4"+
		"\3\4\3\4\3\4\3\5\3\5\3\5\3\5\3\5\3\5\3\5\3\5\3\5\3\5\3\6\3\6\3\6\3\6\3"+
		"\6\3\6\3\6\3\6\3\6\3\6\3\7\3\7\3\7\3\7\3\7\3\7\3\7\3\7\3\7\3\7\3\7\3\7"+
		"\3\b\3\b\3\b\3\b\3\b\3\b\3\b\3\b\3\b\3\t\3\t\3\t\3\t\3\t\3\t\3\t\3\t\3"+
		"\n\3\n\3\n\3\n\3\n\3\n\3\n\3\n\3\n\3\n\3\n\3\n\3\13\3\13\3\13\3\13\3\13"+
		"\3\13\3\13\3\13\3\13\3\13\3\13\3\13\3\f\3\f\3\f\3\f\3\f\3\f\3\f\3\f\3"+
		"\f\3\f\3\f\3\r\3\r\3\r\3\r\3\r\3\r\3\r\3\r\3\16\3\16\3\17\3\17\3\20\3"+
		"\20\3\20\3\20\3\20\3\20\3\21\3\21\3\21\3\21\3\21\3\21\3\21\3\22\3\22\3"+
		"\23\3\23\3\24\3\24\3\24\3\24\3\24\3\24\3\25\3\25\3\25\3\25\3\25\3\26\3"+
		"\26\3\26\3\26\3\26\3\26\3\27\3\27\3\27\3\27\3\27\3\30\3\30\3\30\3\30\3"+
		"\30\3\30\3\31\3\31\3\31\3\31\3\32\3\32\3\33\3\33\3\34\3\34\3\34\3\35\3"+
		"\35\3\35\3\36\3\36\3\36\3\37\3\37\3\37\3 \3 \3 \3 \3!\3!\3!\3\"\3\"\3"+
		"\"\3#\3#\3#\3$\3$\3$\3%\3%\3%\3%\3&\3&\3&\3&\3\'\3\'\3\'\3(\3(\3(\3)\3"+
		")\3*\3*\3+\3+\3,\3,\3,\3-\3-\3.\3.\3/\3/\3\60\3\60\3\60\3\61\3\61\3\61"+
		"\3\62\3\62\3\63\3\63\3\64\3\64\3\64\3\65\3\65\3\65\3\66\3\66\3\66\3\67"+
		"\3\67\3\67\38\38\39\39\3:\3:\3:\3;\3;\3;\3;\3;\3<\3<\3<\3<\3=\3=\3=\3"+
		">\3>\3>\3>\3?\3?\3?\3?\3@\3@\3A\3A\3B\3B\3B\3C\3C\3D\3D\3D\3D\3D\3D\3"+
		"D\3E\3E\3E\3E\3E\3E\3F\3F\3F\3F\3F\3F\3F\3F\3F\3G\3G\3H\3H\3I\3I\3J\3"+
		"J\3K\3K\3K\3L\3L\3L\3M\3M\3M\3N\3N\3N\3O\3O\3O\3P\3P\3P\3Q\3Q\3Q\3R\3"+
		"R\3R\3S\3S\3S\3S\3S\3T\3T\3T\3T\3T\3T\3U\3U\3U\3U\3U\3V\3V\3V\3V\3W\3"+
		"W\3W\3X\3X\3X\3X\3X\3X\3X\3Y\3Y\3Y\3Y\3Y\3Y\3Y\3Z\3Z\3Z\3Z\3Z\3Z\3[\3"+
		"[\3[\3[\3[\3[\3[\3[\3[\3\\\3\\\3\\\3]\3]\3]\3]\3]\3^\3^\3^\3^\3^\3^\3"+
		"_\3_\3_\3_\3_\3_\3`\3`\3`\3`\3`\3`\3a\3a\3a\3a\3a\3b\3b\3b\3b\3b\3b\3"+
		"c\3c\3c\3c\3c\3c\3d\3d\3d\3d\3d\3d\3e\3e\3e\3e\3e\3e\3e\3f\3f\3f\3f\3"+
		"f\3f\3g\3g\3g\3g\3g\3g\3g\3h\3h\3h\3h\3h\3h\3i\3i\3i\3i\3i\3i\3j\3j\3"+
		"j\3j\3k\3k\3k\3l\3l\3l\3l\3l\3l\3m\3m\3m\3m\3m\3m\3m\3n\3n\3n\3n\3n\3"+
		"n\3o\3o\3o\3o\3o\3p\3p\7p\u02f7\np\fp\16p\u02fa\13p\3p\3p\3p\3p\3q\3q"+
		"\7q\u0302\nq\fq\16q\u0305\13q\3q\3q\3r\3r\3r\3r\3s\6s\u030e\ns\rs\16s"+
		"\u030f\3t\3t\3t\3t\3t\3u\3u\7u\u0319\nu\fu\16u\u031c\13u\3v\3v\3v\6v\u0321"+
		"\nv\rv\16v\u0322\5v\u0325\nv\3w\3w\6w\u0329\nw\rw\16w\u032a\3x\3x\6x\u032f"+
		"\nx\rx\16x\u0330\3y\3y\3z\3z\3z\5z\u0338\nz\3z\5z\u033b\nz\3{\6{\u033e"+
		"\n{\r{\16{\u033f\3{\3{\6{\u0344\n{\r{\16{\u0345\5{\u0348\n{\3|\3|\3|\3"+
		"|\5|\u034e\n|\3}\3}\3}\7}\u0353\n}\f}\16}\u0356\13}\3}\3}\3}\3~\3~\3~"+
		"\3~\6~\u035f\n~\r~\16~\u0360\3~\3~\3~\3~\3~\3\177\3\177\3\177\5\177\u036b"+
		"\n\177\3\177\3\177\3\177\3\u0080\3\u0080\3\u0080\3\u0080\3\u0081\3\u0081"+
		"\3\u0081\3\u0360\2\u0082\3\3\5\4\7\5\t\6\13\7\r\b\17\t\21\n\23\13\25\f"+
		"\27\r\31\16\33\17\35\20\37\21!\22#\23%\24\'\25)\26+\27-\30/\31\61\32\63"+
		"\33\65\34\67\359\36;\37= ?!A\"C#E$G%I&K\'M(O)Q*S+U,W-Y.[/]\60_\61a\62"+
		"c\63e\64g\65i\66k\67m8o9q:s;u<w=y>{?}@\177A\u0081B\u0083C\u0085D\u0087"+
		"E\u0089F\u008bG\u008dH\u008fI\u0091J\u0093K\u0095L\u0097M\u0099N\u009b"+
		"O\u009dP\u009fQ\u00a1R\u00a3S\u00a5T\u00a7U\u00a9V\u00abW\u00adX\u00af"+
		"Y\u00b1Z\u00b3[\u00b5\\\u00b7]\u00b9^\u00bb_\u00bd`\u00bfa\u00c1b\u00c3"+
		"c\u00c5d\u00c7e\u00c9f\u00cbg\u00cdh\u00cfi\u00d1j\u00d3k\u00d5l\u00d7"+
		"m\u00d9n\u00dbo\u00ddp\u00dfq\u00e1r\u00e3s\u00e5t\u00e7u\u00e9v\u00eb"+
		"w\u00edx\u00efy\u00f1z\u00f3{\u00f5\2\u00f7\2\u00f9|\u00fb}\u00fd~\u00ff"+
		"\177\u0101\u0080\3\2\n\4\2\f\f\17\17\4\2\13\13\"\"\5\2C\\aac|\6\2\62;"+
		"C\\aac|\5\2\62;CHch\4\2GGgg\4\2--//\6\2\f\f\16\17$$^^\2\u0385\2\3\3\2"+
		"\2\2\2\5\3\2\2\2\2\7\3\2\2\2\2\t\3\2\2\2\2\13\3\2\2\2\2\r\3\2\2\2\2\17"+
		"\3\2\2\2\2\21\3\2\2\2\2\23\3\2\2\2\2\25\3\2\2\2\2\27\3\2\2\2\2\31\3\2"+
		"\2\2\2\33\3\2\2\2\2\35\3\2\2\2\2\37\3\2\2\2\2!\3\2\2\2\2#\3\2\2\2\2%\3"+
		"\2\2\2\2\'\3\2\2\2\2)\3\2\2\2\2+\3\2\2\2\2-\3\2\2\2\2/\3\2\2\2\2\61\3"+
		"\2\2\2\2\63\3\2\2\2\2\65\3\2\2\2\2\67\3\2\2\2\29\3\2\2\2\2;\3\2\2\2\2"+
		"=\3\2\2\2\2?\3\2\2\2\2A\3\2\2\2\2C\3\2\2\2\2E\3\2\2\2\2G\3\2\2\2\2I\3"+
		"\2\2\2\2K\3\2\2\2\2M\3\2\2\2\2O\3\2\2\2\2Q\3\2\2\2\2S\3\2\2\2\2U\3\2\2"+
		"\2\2W\3\2\2\2\2Y\3\2\2\2\2[\3\2\2\2\2]\3\2\2\2\2_\3\2\2\2\2a\3\2\2\2\2"+
		"c\3\2\2\2\2e\3\2\2\2\2g\3\2\2\2\2i\3\2\2\2\2k\3\2\2\2\2m\3\2\2\2\2o\3"+
		"\2\2\2\2q\3\2\2\2\2s\3\2\2\2\2u\3\2\2\2\2w\3\2\2\2\2y\3\2\2\2\2{\3\2\2"+
		"\2\2}\3\2\2\2\2\177\3\2\2\2\2\u0081\3\2\2\2\2\u0083\3\2\2\2\2\u0085\3"+
		"\2\2\2\2\u0087\3\2\2\2\2\u0089\3\2\2\2\2\u008b\3\2\2\2\2\u008d\3\2\2\2"+
		"\2\u008f\3\2\2\2\2\u0091\3\2\2\2\2\u0093\3\2\2\2\2\u0095\3\2\2\2\2\u0097"+
		"\3\2\2\2\2\u0099\3\2\2\2\2\u009b\3\2\2\2\2\u009d\3\2\2\2\2\u009f\3\2\2"+
		"\2\2\u00a1\3\2\2\2\2\u00a3\3\2\2\2\2\u00a5\3\2\2\2\2\u00a7\3\2\2\2\2\u00a9"+
		"\3\2\2\2\2\u00ab\3\2\2\2\2\u00ad\3\2\2\2\2\u00af\3\2\2\2\2\u00b1\3\2\2"+
		"\2\2\u00b3\3\2\2\2\2\u00b5\3\2\2\2\2\u00b7\3\2\2\2\2\u00b9\3\2\2\2\2\u00bb"+
		"\3\2\2\2\2\u00bd\3\2\2\2\2\u00bf\3\2\2\2\2\u00c1\3\2\2\2\2\u00c3\3\2\2"+
		"\2\2\u00c5\3\2\2\2\2\u00c7\3\2\2\2\2\u00c9\3\2\2\2\2\u00cb\3\2\2\2\2\u00cd"+
		"\3\2\2\2\2\u00cf\3\2\2\2\2\u00d1\3\2\2\2\2\u00d3\3\2\2\2\2\u00d5\3\2\2"+
		"\2\2\u00d7\3\2\2\2\2\u00d9\3\2\2\2\2\u00db\3\2\2\2\2\u00dd\3\2\2\2\2\u00df"+
		"\3\2\2\2\2\u00e1\3\2\2\2\2\u00e3\3\2\2\2\2\u00e5\3\2\2\2\2\u00e7\3\2\2"+
		"\2\2\u00e9\3\2\2\2\2\u00eb\3\2\2\2\2\u00ed\3\2\2\2\2\u00ef\3\2\2\2\2\u00f1"+
		"\3\2\2\2\2\u00f3\3\2\2\2\2\u00f9\3\2\2\2\2\u00fb\3\2\2\2\2\u00fd\3\2\2"+
		"\2\2\u00ff\3\2\2\2\2\u0101\3\2\2\2\3\u0103\3\2\2\2\5\u0105\3\2\2\2\7\u010a"+
		"\3\2\2\2\t\u0112\3\2\2\2\13\u011c\3\2\2\2\r\u0126\3\2\2\2\17\u0132\3\2"+
		"\2\2\21\u013b\3\2\2\2\23\u0143\3\2\2\2\25\u014f\3\2\2\2\27\u015b\3\2\2"+
		"\2\31\u0166\3\2\2\2\33\u016e\3\2\2\2\35\u0170\3\2\2\2\37\u0172\3\2\2\2"+
		"!\u0178\3\2\2\2#\u017f\3\2\2\2%\u0181\3\2\2\2\'\u0183\3\2\2\2)\u0189\3"+
		"\2\2\2+\u018e\3\2\2\2-\u0194\3\2\2\2/\u0199\3\2\2\2\61\u019f\3\2\2\2\63"+
		"\u01a3\3\2\2\2\65\u01a5\3\2\2\2\67\u01a7\3\2\2\29\u01aa\3\2\2\2;\u01ad"+
		"\3\2\2\2=\u01b0\3\2\2\2?\u01b3\3\2\2\2A\u01b7\3\2\2\2C\u01ba\3\2\2\2E"+
		"\u01bd\3\2\2\2G\u01c0\3\2\2\2I\u01c3\3\2\2\2K\u01c7\3\2\2\2M\u01cb\3\2"+
		"\2\2O\u01ce\3\2\2\2Q\u01d1\3\2\2\2S\u01d3\3\2\2\2U\u01d5\3\2\2\2W\u01d7"+
		"\3\2\2\2Y\u01da\3\2\2\2[\u01dc\3\2\2\2]\u01de\3\2\2\2_\u01e0\3\2\2\2a"+
		"\u01e3\3\2\2\2c\u01e6\3\2\2\2e\u01e8\3\2\2\2g\u01ea\3\2\2\2i\u01ed\3\2"+
		"\2\2k\u01f0\3\2\2\2m\u01f3\3\2\2\2o\u01f6\3\2\2\2q\u01f8\3\2\2\2s\u01fa"+
		"\3\2\2\2u\u01fd\3\2\2\2w\u0202\3\2\2\2y\u0206\3\2\2\2{\u0209\3\2\2\2}"+
		"\u020d\3\2\2\2\177\u0211\3\2\2\2\u0081\u0213\3\2\2\2\u0083\u0215\3\2\2"+
		"\2\u0085\u0218\3\2\2\2\u0087\u021a\3\2\2\2\u0089\u0221\3\2\2\2\u008b\u0227"+
		"\3\2\2\2\u008d\u0230\3\2\2\2\u008f\u0232\3\2\2\2\u0091\u0234\3\2\2\2\u0093"+
		"\u0236\3\2\2\2\u0095\u0238\3\2\2\2\u0097\u023b\3\2\2\2\u0099\u023e\3\2"+
		"\2\2\u009b\u0241\3\2\2\2\u009d\u0244\3\2\2\2\u009f\u0247\3\2\2\2\u00a1"+
		"\u024a\3\2\2\2\u00a3\u024d\3\2\2\2\u00a5\u0250\3\2\2\2\u00a7\u0255\3\2"+
		"\2\2\u00a9\u025b\3\2\2\2\u00ab\u0260\3\2\2\2\u00ad\u0264\3\2\2\2\u00af"+
		"\u0267\3\2\2\2\u00b1\u026e\3\2\2\2\u00b3\u0275\3\2\2\2\u00b5\u027b\3\2"+
		"\2\2\u00b7\u0284\3\2\2\2\u00b9\u0287\3\2\2\2\u00bb\u028c\3\2\2\2\u00bd"+
		"\u0292\3\2\2\2\u00bf\u0298\3\2\2\2\u00c1\u029e\3\2\2\2\u00c3\u02a3\3\2"+
		"\2\2\u00c5\u02a9\3\2\2\2\u00c7\u02af\3\2\2\2\u00c9\u02b5\3\2\2\2\u00cb"+
		"\u02bc\3\2\2\2\u00cd\u02c2\3\2\2\2\u00cf\u02c9\3\2\2\2\u00d1\u02cf\3\2"+
		"\2\2\u00d3\u02d5\3\2\2\2\u00d5\u02d9\3\2\2\2\u00d7\u02dc\3\2\2\2\u00d9"+
		"\u02e2\3\2\2\2\u00db\u02e9\3\2\2\2\u00dd\u02ef\3\2\2\2\u00df\u02f4\3\2"+
		"\2\2\u00e1\u02ff\3\2\2\2\u00e3\u0308\3\2\2\2\u00e5\u030d\3\2\2\2\u00e7"+
		"\u0311\3\2\2\2\u00e9\u0316\3\2\2\2\u00eb\u0324\3\2\2\2\u00ed\u0326\3\2"+
		"\2\2\u00ef\u032c\3\2\2\2\u00f1\u0332\3\2\2\2\u00f3\u0334\3\2\2\2\u00f5"+
		"\u033d\3\2\2\2\u00f7\u034d\3\2\2\2\u00f9\u034f\3\2\2\2\u00fb\u035a\3\2"+
		"\2\2\u00fd\u0367\3\2\2\2\u00ff\u036f\3\2\2\2\u0101\u0373\3\2\2\2\u0103"+
		"\u0104\7<\2\2\u0104\4\3\2\2\2\u0105\u0106\7i\2\2\u0106\u0107\7q\2\2\u0107"+
		"\u0108\7v\2\2\u0108\u0109\7q\2\2\u0109\6\3\2\2\2\u010a\u010b\7\'\2\2\u010b"+
		"\u010c\7q\2\2\u010c\u010d\7w\2\2\u010d\u010e\7v\2\2\u010e\u010f\7r\2\2"+
		"\u010f\u0110\7w\2\2\u0110\u0111\7v\2\2\u0111\b\3\2\2\2\u0112\u0113\7\'"+
		"\2\2\u0113\u0114\7n\2\2\u0114\u0115\7c\2\2\u0115\u0116\7w\2\2\u0116\u0117"+
		"\7p\2\2\u0117\u0118\7e\2\2\u0118\u0119\7j\2\2\u0119\u011a\7g\2\2\u011a"+
		"\u011b\7t\2\2\u011b\n\3\2\2\2\u011c\u011d\7\'\2\2\u011d\u011e\7|\2\2\u011e"+
		"\u011f\7g\2\2\u011f\u0120\7t\2\2\u0120\u0121\7q\2\2\u0121\u0122\7r\2\2"+
		"\u0122\u0123\7c\2\2\u0123\u0124\7i\2\2\u0124\u0125\7g\2\2\u0125\f\3\2"+
		"\2\2\u0126\u0127\7\'\2\2\u0127\u0128\7|\2\2\u0128\u0129\7r\2\2\u0129\u012a"+
		"\7t\2\2\u012a\u012b\7g\2\2\u012b\u012c\7u\2\2\u012c\u012d\7g\2\2\u012d"+
		"\u012e\7t\2\2\u012e\u012f\7x\2\2\u012f\u0130\7g\2\2\u0130\u0131\7f\2\2"+
		"\u0131\16\3\2\2\2\u0132\u0133\7\'\2\2\u0133\u0134\7c\2\2\u0134\u0135\7"+
		"f\2\2\u0135\u0136\7f\2\2\u0136\u0137\7t\2\2\u0137\u0138\7g\2\2\u0138\u0139"+
		"\7u\2\2\u0139\u013a\7u\2\2\u013a\20\3\2\2\2\u013b\u013c\7\'\2\2\u013c"+
		"\u013d\7k\2\2\u013d\u013e\7o\2\2\u013e\u013f\7r\2\2\u013f\u0140\7q\2\2"+
		"\u0140\u0141\7t\2\2\u0141\u0142\7v\2\2\u0142\22\3\2\2\2\u0143\u0144\7"+
		"\'\2\2\u0144\u0145\7d\2\2\u0145\u0146\7t\2\2\u0146\u0147\7g\2\2\u0147"+
		"\u0148\7c\2\2\u0148\u0149\7m\2\2\u0149\u014a\7r\2\2\u014a\u014b\7q\2\2"+
		"\u014b\u014c\7k\2\2\u014c\u014d\7p\2\2\u014d\u014e\7v\2\2\u014e\24\3\2"+
		"\2\2\u014f\u0150\7\'\2\2\u0150\u0151\7c\2\2\u0151\u0152\7u\2\2\u0152\u0153"+
		"\7o\2\2\u0153\u0154\7k\2\2\u0154\u0155\7p\2\2\u0155\u0156\7e\2\2\u0156"+
		"\u0157\7n\2\2\u0157\u0158\7w\2\2\u0158\u0159\7f\2\2\u0159\u015a\7g\2\2"+
		"\u015a\26\3\2\2\2\u015b\u015c\7\'\2\2\u015c\u015d\7c\2\2\u015d\u015e\7"+
		"u\2\2\u015e\u015f\7o\2\2\u015f\u0160\7d\2\2\u0160\u0161\7k\2\2\u0161\u0162"+
		"\7p\2\2\u0162\u0163\7c\2\2\u0163\u0164\7t\2\2\u0164\u0165\7{\2\2\u0165"+
		"\30\3\2\2\2\u0166\u0167\7\'\2\2\u0167\u0168\7q\2\2\u0168\u0169\7r\2\2"+
		"\u0169\u016a\7v\2\2\u016a\u016b\7k\2\2\u016b\u016c\7q\2\2\u016c\u016d"+
		"\7p\2\2\u016d\32\3\2\2\2\u016e\u016f\7.\2\2\u016f\34\3\2\2\2\u0170\u0171"+
		"\7?\2\2\u0171\36\3\2\2\2\u0172\u0173\7e\2\2\u0173\u0174\7q\2\2\u0174\u0175"+
		"\7p\2\2\u0175\u0176\7u\2\2\u0176\u0177\7v\2\2\u0177 \3\2\2\2\u0178\u0179"+
		"\7u\2\2\u0179\u017a\7v\2\2\u017a\u017b\7t\2\2\u017b\u017c\7w\2\2\u017c"+
		"\u017d\7e\2\2\u017d\u017e\7v\2\2\u017e\"\3\2\2\2\u017f\u0180\7}\2\2\u0180"+
		"$\3\2\2\2\u0181\u0182\7\177\2\2\u0182&\3\2\2\2\u0183\u0184\7w\2\2\u0184"+
		"\u0185\7d\2\2\u0185\u0186\7{\2\2\u0186\u0187\7v\2\2\u0187\u0188\7g\2\2"+
		"\u0188(\3\2\2\2\u0189\u018a\7d\2\2\u018a\u018b\7{\2\2\u018b\u018c\7v\2"+
		"\2\u018c\u018d\7g\2\2\u018d*\3\2\2\2\u018e\u018f\7w\2\2\u018f\u0190\7"+
		"y\2\2\u0190\u0191\7q\2\2\u0191\u0192\7t\2\2\u0192\u0193\7f\2\2\u0193,"+
		"\3\2\2\2\u0194\u0195\7y\2\2\u0195\u0196\7q\2\2\u0196\u0197\7t\2\2\u0197"+
		"\u0198\7f\2\2\u0198.\3\2\2\2\u0199\u019a\7h\2\2\u019a\u019b\7n\2\2\u019b"+
		"\u019c\7q\2\2\u019c\u019d\7c\2\2\u019d\u019e\7v\2\2\u019e\60\3\2\2\2\u019f"+
		"\u01a0\7u\2\2\u01a0\u01a1\7v\2\2\u01a1\u01a2\7t\2\2\u01a2\62\3\2\2\2\u01a3"+
		"\u01a4\7]\2\2\u01a4\64\3\2\2\2\u01a5\u01a6\7_\2\2\u01a6\66\3\2\2\2\u01a7"+
		"\u01a8\7-\2\2\u01a8\u01a9\7?\2\2\u01a98\3\2\2\2\u01aa\u01ab\7/\2\2\u01ab"+
		"\u01ac\7?\2\2\u01ac:\3\2\2\2\u01ad\u01ae\7\61\2\2\u01ae\u01af\7?\2\2\u01af"+
		"<\3\2\2\2\u01b0\u01b1\7,\2\2\u01b1\u01b2\7?\2\2\u01b2>\3\2\2\2\u01b3\u01b4"+
		"\7,\2\2\u01b4\u01b5\7,\2\2\u01b5\u01b6\7?\2\2\u01b6@\3\2\2\2\u01b7\u01b8"+
		"\7(\2\2\u01b8\u01b9\7?\2\2\u01b9B\3\2\2\2\u01ba\u01bb\7~\2\2\u01bb\u01bc"+
		"\7?\2\2\u01bcD\3\2\2\2\u01bd\u01be\7`\2\2\u01be\u01bf\7?\2\2\u01bfF\3"+
		"\2\2\2\u01c0\u01c1\7\'\2\2\u01c1\u01c2\7?\2\2\u01c2H\3\2\2\2\u01c3\u01c4"+
		"\7>\2\2\u01c4\u01c5\7>\2\2\u01c5\u01c6\7?\2\2\u01c6J\3\2\2\2\u01c7\u01c8"+
		"\7@\2\2\u01c8\u01c9\7@\2\2\u01c9\u01ca\7?\2\2\u01caL\3\2\2\2\u01cb\u01cc"+
		"\7-\2\2\u01cc\u01cd\7-\2\2\u01cdN\3\2\2\2\u01ce\u01cf\7/\2\2\u01cf\u01d0"+
		"\7/\2\2\u01d0P\3\2\2\2\u01d1\u01d2\7-\2\2\u01d2R\3\2\2\2\u01d3\u01d4\7"+
		"/\2\2\u01d4T\3\2\2\2\u01d5\u01d6\7\u0080\2\2\u01d6V\3\2\2\2\u01d7\u01d8"+
		"\7,\2\2\u01d8\u01d9\7,\2\2\u01d9X\3\2\2\2\u01da\u01db\7,\2\2\u01dbZ\3"+
		"\2\2\2\u01dc\u01dd\7\61\2\2\u01dd\\\3\2\2\2\u01de\u01df\7\'\2\2\u01df"+
		"^\3\2\2\2\u01e0\u01e1\7>\2\2\u01e1\u01e2\7>\2\2\u01e2`\3\2\2\2\u01e3\u01e4"+
		"\7@\2\2\u01e4\u01e5\7@\2\2\u01e5b\3\2\2\2\u01e6\u01e7\7>\2\2\u01e7d\3"+
		"\2\2\2\u01e8\u01e9\7@\2\2\u01e9f\3\2\2\2\u01ea\u01eb\7>\2\2\u01eb\u01ec"+
		"\7?\2\2\u01ech\3\2\2\2\u01ed\u01ee\7@\2\2\u01ee\u01ef\7?\2\2\u01efj\3"+
		"\2\2\2\u01f0\u01f1\7?\2\2\u01f1\u01f2\7?\2\2\u01f2l\3\2\2\2\u01f3\u01f4"+
		"\7#\2\2\u01f4\u01f5\7?\2\2\u01f5n\3\2\2\2\u01f6\u01f7\7`\2\2\u01f7p\3"+
		"\2\2\2\u01f8\u01f9\7~\2\2\u01f9r\3\2\2\2\u01fa\u01fb\7v\2\2\u01fb\u01fc"+
		"\7q\2\2\u01fct\3\2\2\2\u01fd\u01fe\7u\2\2\u01fe\u01ff\7v\2\2\u01ff\u0200"+
		"\7g\2\2\u0200\u0201\7r\2\2\u0201v\3\2\2\2\u0202\u0203\7c\2\2\u0203\u0204"+
		"\7p\2\2\u0204\u0205\7f\2\2\u0205x\3\2\2\2\u0206\u0207\7q\2\2\u0207\u0208"+
		"\7t\2\2\u0208z\3\2\2\2\u0209\u020a\7z\2\2\u020a\u020b\7q\2\2\u020b\u020c"+
		"\7t\2\2\u020c|\3\2\2\2\u020d\u020e\7p\2\2\u020e\u020f\7q\2\2\u020f\u0210"+
		"\7v\2\2\u0210~\3\2\2\2\u0211\u0212\7*\2\2\u0212\u0080\3\2\2\2\u0213\u0214"+
		"\7+\2\2\u0214\u0082\3\2\2\2\u0215\u0216\7c\2\2\u0216\u0217\7u\2\2\u0217"+
		"\u0084\3\2\2\2\u0218\u0219\7B\2\2\u0219\u0086\3\2\2\2\u021a\u021b\7t\2"+
		"\2\u021b\u021c\7g\2\2\u021c\u021d\7v\2\2\u021d\u021e\7w\2\2\u021e\u021f"+
		"\7t\2\2\u021f\u0220\7p\2\2\u0220\u0088\3\2\2\2\u0221\u0222\7d\2\2\u0222"+
		"\u0223\7t\2\2\u0223\u0224\7g\2\2\u0224\u0225\7c\2\2\u0225\u0226\7m\2\2"+
		"\u0226\u008a\3\2\2\2\u0227\u0228\7e\2\2\u0228\u0229\7q\2\2\u0229\u022a"+
		"\7p\2\2\u022a\u022b\7v\2\2\u022b\u022c\7k\2\2\u022c\u022d\7p\2\2\u022d"+
		"\u022e\7w\2\2\u022e\u022f\7g\2\2\u022f\u008c\3\2\2\2\u0230\u0231\7\60"+
		"\2\2\u0231\u008e\3\2\2\2\u0232\u0233\7C\2\2\u0233\u0090\3\2\2\2\u0234"+
		"\u0235\7Z\2\2\u0235\u0092\3\2\2\2\u0236\u0237\7[\2\2\u0237\u0094\3\2\2"+
		"\2\u0238\u0239\7C\2\2\u0239\u023a\7Z\2\2\u023a\u0096\3\2\2\2\u023b\u023c"+
		"\7C\2\2\u023c\u023d\7[\2\2\u023d\u0098\3\2\2\2\u023e\u023f\7Z\2\2\u023f"+
		"\u0240\7[\2\2\u0240\u009a\3\2\2\2\u0241\u0242\7R\2\2\u0242\u0243\7e\2"+
		"\2\u0243\u009c\3\2\2\2\u0244\u0245\7R\2\2\u0245\u0246\7|\2\2\u0246\u009e"+
		"\3\2\2\2\u0247\u0248\7R\2\2\u0248\u0249\7p\2\2\u0249\u00a0\3\2\2\2\u024a"+
		"\u024b\7R\2\2\u024b\u024c\7x\2\2\u024c\u00a2\3\2\2\2\u024d\u024e\7\60"+
		"\2\2\u024e\u024f\7y\2\2\u024f\u00a4\3\2\2\2\u0250\u0251\7v\2\2\u0251\u0252"+
		"\7t\2\2\u0252\u0253\7w\2\2\u0253\u0254\7g\2\2\u0254\u00a6\3\2\2\2\u0255"+
		"\u0256\7h\2\2\u0256\u0257\7c\2\2\u0257\u0258\7n\2\2\u0258\u0259\7u\2\2"+
		"\u0259\u025a\7g\2\2\u025a\u00a8\3\2\2\2\u025b\u025c\7\'\2\2\u025c\u025d"+
		"\7c\2\2\u025d\u025e\7u\2\2\u025e\u025f\7o\2\2\u025f\u00aa\3\2\2\2\u0260"+
		"\u0261\7u\2\2\u0261\u0262\7w\2\2\u0262\u0263\7d\2\2\u0263\u00ac\3\2\2"+
		"\2\u0264\u0265\7/\2\2\u0265\u0266\7@\2\2\u0266\u00ae\3\2\2\2\u0267\u0268"+
		"\7c\2\2\u0268\u0269\7u\2\2\u0269\u026a\7o\2\2\u026a\u026b\7u\2\2\u026b"+
		"\u026c\7w\2\2\u026c\u026d\7d\2\2\u026d\u00b0\3\2\2\2\u026e\u026f\7t\2"+
		"\2\u026f\u0270\7q\2\2\u0270\u0271\7o\2\2\u0271\u0272\7u\2\2\u0272\u0273"+
		"\7w\2\2\u0273\u0274\7d\2\2\u0274\u00b2\3\2\2\2\u0275\u0276\7u\2\2\u0276"+
		"\u0277\7v\2\2\u0277\u0278\7c\2\2\u0278\u0279\7e\2\2\u0279\u027a\7m\2\2"+
		"\u027a\u00b4\3\2\2\2\u027b\u027c\7e\2\2\u027c\u027d\7n\2\2\u027d\u027e"+
		"\7q\2\2\u027e\u027f\7d\2\2\u027f\u0280\7d\2\2\u0280\u0281\7g\2\2\u0281"+
		"\u0282\7t\2\2\u0282\u0283\7u\2\2\u0283\u00b6\3\2\2\2\u0284\u0285\7k\2"+
		"\2\u0285\u0286\7h\2\2\u0286\u00b8\3\2\2\2\u0287\u0288\7g\2\2\u0288\u0289"+
		"\7n\2\2\u0289\u028a\7u\2\2\u028a\u028b\7g\2\2\u028b\u00ba\3\2\2\2\u028c"+
		"\u028d\7k\2\2\u028d\u028e\7h\2\2\u028e\u028f\7a\2\2\u028f\u0290\7e\2\2"+
		"\u0290\u0291\7u\2\2\u0291\u00bc\3\2\2\2\u0292\u0293\7k\2\2\u0293\u0294"+
		"\7h\2\2\u0294\u0295\7a\2\2\u0295\u0296\7e\2\2\u0296\u0297\7e\2\2\u0297"+
		"\u00be\3\2\2\2\u0298\u0299\7k\2\2\u0299\u029a\7h\2\2\u029a\u029b\7a\2"+
		"\2\u029b\u029c\7g\2\2\u029c\u029d\7s\2\2\u029d\u00c0\3\2\2\2\u029e\u029f"+
		"\7k\2\2\u029f\u02a0\7h\2\2\u02a0\u02a1\7a\2\2\u02a1\u02a2\7|\2\2\u02a2"+
		"\u00c2\3\2\2\2\u02a3\u02a4\7k\2\2\u02a4\u02a5\7h\2\2\u02a5\u02a6\7a\2"+
		"\2\u02a6\u02a7\7p\2\2\u02a7\u02a8\7g\2\2\u02a8\u00c4\3\2\2\2\u02a9\u02aa"+
		"\7k\2\2\u02aa\u02ab\7h\2\2\u02ab\u02ac\7a\2\2\u02ac\u02ad\7p\2\2\u02ad"+
		"\u02ae\7|\2\2\u02ae\u00c6\3\2\2\2\u02af\u02b0\7k\2\2\u02b0\u02b1\7h\2"+
		"\2\u02b1\u02b2\7a\2\2\u02b2\u02b3\7r\2\2\u02b3\u02b4\7n\2\2\u02b4\u00c8"+
		"\3\2\2\2\u02b5\u02b6\7k\2\2\u02b6\u02b7\7h\2\2\u02b7\u02b8\7a\2\2\u02b8"+
		"\u02b9\7r\2\2\u02b9\u02ba\7q\2\2\u02ba\u02bb\7u\2\2\u02bb\u00ca\3\2\2"+
		"\2\u02bc\u02bd\7k\2\2\u02bd\u02be\7h\2\2\u02be\u02bf\7a\2\2\u02bf\u02c0"+
		"\7o\2\2\u02c0\u02c1\7k\2\2\u02c1\u00cc\3\2\2\2\u02c2\u02c3\7k\2\2\u02c3"+
		"\u02c4\7h\2\2\u02c4\u02c5\7a\2\2\u02c5\u02c6\7p\2\2\u02c6\u02c7\7g\2\2"+
		"\u02c7\u02c8\7i\2\2\u02c8\u00ce\3\2\2\2\u02c9\u02ca\7k\2\2\u02ca\u02cb"+
		"\7h\2\2\u02cb\u02cc\7a\2\2\u02cc\u02cd\7x\2\2\u02cd\u02ce\7u\2\2\u02ce"+
		"\u00d0\3\2\2\2\u02cf\u02d0\7k\2\2\u02d0\u02d1\7h\2\2\u02d1\u02d2\7a\2"+
		"\2\u02d2\u02d3\7x\2\2\u02d3\u02d4\7e\2\2\u02d4\u00d2\3\2\2\2\u02d5\u02d6"+
		"\7h\2\2\u02d6\u02d7\7q\2\2\u02d7\u02d8\7t\2\2\u02d8\u00d4\3\2\2\2\u02d9"+
		"\u02da\7k\2\2\u02da\u02db\7p\2\2\u02db\u00d6\3\2\2\2\u02dc\u02dd\7y\2"+
		"\2\u02dd\u02de\7j\2\2\u02de\u02df\7k\2\2\u02df\u02e0\7n\2\2\u02e0\u02e1"+
		"\7g\2\2\u02e1\u00d8\3\2\2\2\u02e2\u02e3\7t\2\2\u02e3\u02e4\7g\2\2\u02e4"+
		"\u02e5\7r\2\2\u02e5\u02e6\7g\2\2\u02e6\u02e7\7c\2\2\u02e7\u02e8\7v\2\2"+
		"\u02e8\u00da\3\2\2\2\u02e9\u02ea\7w\2\2\u02ea\u02eb\7p\2\2\u02eb\u02ec"+
		"\7v\2\2\u02ec\u02ed\7k\2\2\u02ed\u02ee\7n\2\2\u02ee\u00dc\3\2\2\2\u02ef"+
		"\u02f0\7y\2\2\u02f0\u02f1\7j\2\2\u02f1\u02f2\7g\2\2\u02f2\u02f3\7p\2\2"+
		"\u02f3\u00de\3\2\2\2\u02f4\u02f8\t\2\2\2\u02f5\u02f7\t\3\2\2\u02f6\u02f5"+
		"\3\2\2\2\u02f7\u02fa\3\2\2\2\u02f8\u02f6\3\2\2\2\u02f8\u02f9\3\2\2\2\u02f9"+
		"\u02fb\3\2\2\2\u02fa\u02f8\3\2\2\2\u02fb\u02fc\5\u00e1q\2\u02fc\u02fd"+
		"\3\2\2\2\u02fd\u02fe\bp\2\2\u02fe\u00e0\3\2\2\2\u02ff\u0303\7=\2\2\u0300"+
		"\u0302\n\2\2\2\u0301\u0300\3\2\2\2\u0302\u0305\3\2\2\2\u0303\u0301\3\2"+
		"\2\2\u0303\u0304\3\2\2\2\u0304\u0306\3\2\2\2\u0305\u0303\3\2\2\2\u0306"+
		"\u0307\bq\2\2\u0307\u00e2\3\2\2\2\u0308\u0309\t\3\2\2\u0309\u030a\3\2"+
		"\2\2\u030a\u030b\br\3\2\u030b\u00e4\3\2\2\2\u030c\u030e\t\2\2\2\u030d"+
		"\u030c\3\2\2\2\u030e\u030f\3\2\2\2\u030f\u030d\3\2\2\2\u030f\u0310\3\2"+
		"\2\2\u0310\u00e6\3\2\2\2\u0311\u0312\7x\2\2\u0312\u0313\7q\2\2\u0313\u0314"+
		"\7k\2\2\u0314\u0315\7f\2\2\u0315\u00e8\3\2\2\2\u0316\u031a\t\4\2\2\u0317"+
		"\u0319\t\5\2\2\u0318\u0317\3\2\2\2\u0319\u031c\3\2\2\2\u031a\u0318\3\2"+
		"\2\2\u031a\u031b\3\2\2\2\u031b\u00ea\3\2\2\2\u031c\u031a\3\2\2\2\u031d"+
		"\u0325\4\62;\2\u031e\u0320\4\63;\2\u031f\u0321\4\62;\2\u0320\u031f\3\2"+
		"\2\2\u0321\u0322\3\2\2\2\u0322\u0320\3\2\2\2\u0322\u0323\3\2\2\2\u0323"+
		"\u0325\3\2\2\2\u0324\u031d\3\2\2\2\u0324\u031e\3\2\2\2\u0325\u00ec\3\2"+
		"\2\2\u0326\u0328\7&\2\2\u0327\u0329\t\6\2\2\u0328\u0327\3\2\2\2\u0329"+
		"\u032a\3\2\2\2\u032a\u0328\3\2\2\2\u032a\u032b\3\2\2\2\u032b\u00ee\3\2"+
		"\2\2\u032c\u032e\7\'\2\2\u032d\u032f\4\62\63\2\u032e\u032d\3\2\2\2\u032f"+
		"\u0330\3\2\2\2\u0330\u032e\3\2\2\2\u0330\u0331\3\2\2\2\u0331\u00f0\3\2"+
		"\2\2\u0332\u0333\7(\2\2\u0333\u00f2\3\2\2\2\u0334\u033a\5\u00f5{\2\u0335"+
		"\u0337\t\7\2\2\u0336\u0338\t\b\2\2\u0337\u0336\3\2\2\2\u0337\u0338\3\2"+
		"\2\2\u0338\u0339\3\2\2\2\u0339\u033b\5\u00f5{\2\u033a\u0335\3\2\2\2\u033a"+
		"\u033b\3\2\2\2\u033b\u00f4\3\2\2\2\u033c\u033e\4\62;\2\u033d\u033c\3\2"+
		"\2\2\u033e\u033f\3\2\2\2\u033f\u033d\3\2\2\2\u033f\u0340\3\2\2\2\u0340"+
		"\u0347\3\2\2\2\u0341\u0343\7\60\2\2\u0342\u0344\4\62;\2\u0343\u0342\3"+
		"\2\2\2\u0344\u0345\3\2\2\2\u0345\u0343\3\2\2\2\u0345\u0346\3\2\2\2\u0346"+
		"\u0348\3\2\2\2\u0347\u0341\3\2\2\2\u0347\u0348\3\2\2\2\u0348\u00f6\3\2"+
		"\2\2\u0349\u034a\7^\2\2\u034a\u034e\13\2\2\2\u034b\u034c\7^\2\2\u034c"+
		"\u034e\5\u00e5s\2\u034d\u0349\3\2\2\2\u034d\u034b\3\2\2\2\u034e\u00f8"+
		"\3\2\2\2\u034f\u0354\7$\2\2\u0350\u0353\5\u00f7|\2\u0351\u0353\n\t\2\2"+
		"\u0352\u0350\3\2\2\2\u0352\u0351\3\2\2\2\u0353\u0356\3\2\2\2\u0354\u0352"+
		"\3\2\2\2\u0354\u0355\3\2\2\2\u0355\u0357\3\2\2\2\u0356\u0354\3\2\2\2\u0357"+
		"\u0358\7$\2\2\u0358\u0359\b}\4\2\u0359\u00fa\3\2\2\2\u035a\u035b\7}\2"+
		"\2\u035b\u035c\7}\2\2\u035c\u035e\3\2\2\2\u035d\u035f\13\2\2\2\u035e\u035d"+
		"\3\2\2\2\u035f\u0360\3\2\2\2\u0360\u0361\3\2\2\2\u0360\u035e\3\2\2\2\u0361"+
		"\u0362\3\2\2\2\u0362\u0363\7\177\2\2\u0363\u0364\7\177\2\2\u0364\u0365"+
		"\3\2\2\2\u0365\u0366\b~\5\2\u0366\u00fc\3\2\2\2\u0367\u036a\7)\2\2\u0368"+
		"\u036b\5\u00f7|\2\u0369\u036b\n\t\2\2\u036a\u0368\3\2\2\2\u036a\u0369"+
		"\3\2\2\2\u036b\u036c\3\2\2\2\u036c\u036d\7)\2\2\u036d\u036e\b\177\6\2"+
		"\u036e\u00fe\3\2\2\2\u036f\u0370\7B\2\2\u0370\u0371\7|\2\2\u0371\u0372"+
		"\7r\2\2\u0372\u0100\3\2\2\2\u0373\u0374\7]\2\2\u0374\u0375\7_\2\2\u0375"+
		"\u0102\3\2\2\2\26\2\u02f8\u0303\u030f\u031a\u0322\u0324\u0328\u032a\u0330"+
		"\u0337\u033a\u033f\u0345\u0347\u034d\u0352\u0354\u0360\u036a\7\2\3\2\b"+
		"\2\2\3}\2\3~\3\3\177\4";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}