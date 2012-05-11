package ctagsinterface.index;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Set;
import java.util.Vector;

import javax.swing.JOptionPane;

import org.apache.lucene.analysis.KeywordAnalyzer;
import org.apache.lucene.analysis.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Fieldable;
import org.apache.lucene.document.Field.Index;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.index.IndexNotFoundException;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;
import org.gjt.sp.jedit.GUIUtilities;
import org.gjt.sp.jedit.jEdit;
import org.gjt.sp.util.Log;

import ctagsinterface.main.Logger;
import ctagsinterface.main.Tag;

/*
 * TagIndex manages a Lucece index with the tag information.
 * Usage:
 * --- General ---
 * - When the plugin is started, create a new TagIndex object.
 * - When the plugin is stopped, call close() to release any resources.
 * --- Index creation ---
 * - Before indexing a set of files, call startActivity().
 * - Add tags for the set of files using insertTag().
 *   Use getOrigin() to get an object representing the origin of the tags.
 * - When done, call endActivity(). This will commit the changes to make
 *   them available for searching.
 * --- Searching the index ---
 * - Call queryTag() with the tag name and a list of tags (for output).
 */
public class TagIndex
{
	public static final String ORIGIN_FLD = "origin";
	public static final String _ORIGIN_FLD = "_origin";
	public static final String TYPE_FLD = "type";
	public static final String DOCTYPE_FLD = "doctype";
	public static final String _PATH_FLD = "_path";
	public static final String PATH_FLD = "path";
	public static final String PATTERN_FLD = "pattern";
	public static final String _NAME_FLD = "_name";
	public static final String _NAME_LOWERCASE_FLD = "_nameLC";
	public static final String LINE_FLD = "line";
	public static final String ORIGIN_DOC_TYPE = "origin";
	public static final String TAG_DOC_TYPE = "tag";
	public static final String ORIGIN_ID_FLD = "id";
	public static final String LANGUAGE = "language";
	public static final int MAX_RESULTS = 1000;
	private FSDirectory directory;
	private IndexWriter writer;
	private PerFieldAnalyzerWrapper analyzer;
	private StandardAnalyzer standardAnalyzer;
	private KeywordAnalyzer keywordAnalyzer;
	private static final String[] FIXED_FIELDS = {
		_NAME_FLD, _NAME_LOWERCASE_FLD, PATTERN_FLD, PATH_FLD,
		_PATH_FLD, DOCTYPE_FLD, ORIGIN_FLD, _ORIGIN_FLD
	};
	private static Set<String> fixedFields;
	private int writeCount;
	public enum OriginType
	{
		PROJECT("Project"),
		DIRECTORY("Directory"),
		ARCHIVE("Archive"),
		TAGFILE("TagFile"),
		MISC("Misc");
		private OriginType(String name)
		{
			this.name = name;
		}
		public static OriginType fromString(String s)
		{
			for (OriginType type: OriginType.values())
				if (type.name.equals(s))
					return type;
			return OriginType.MISC;
		}
		public String name;
	}

	public TagIndex() throws RuntimeException
	{
		File path = new File(getIndexPath());
		path.mkdirs();
		standardAnalyzer = new StandardAnalyzer(Version.LUCENE_30,
			new HashSet<String>());
		keywordAnalyzer = new KeywordAnalyzer();
		analyzer = new PerFieldAnalyzerWrapper(standardAnalyzer);
		// Tag documents
		analyzer.addAnalyzer(_NAME_FLD, keywordAnalyzer);
		analyzer.addAnalyzer(_NAME_LOWERCASE_FLD, keywordAnalyzer);
		analyzer.addAnalyzer(_PATH_FLD, keywordAnalyzer);
		analyzer.addAnalyzer(_ORIGIN_FLD, keywordAnalyzer);
		// Origin documents
		analyzer.addAnalyzer(ORIGIN_ID_FLD, keywordAnalyzer);
		analyzer.addAnalyzer(TYPE_FLD, keywordAnalyzer);
		fixedFields = new HashSet<String>();
		for (String s: FIXED_FIELDS)
			fixedFields.add(s);
		writeCount = 0;
		try
		{
			directory = FSDirectory.open(path);
			if (IndexWriter.isLocked(directory))
			{
				Log.log(Log.WARNING, this, "The lucene index at " + path.getAbsolutePath() + " is locked");
				int ret = GUIUtilities.confirm(jEdit.getActiveView(),
					"lucene.index.locked", new Object[]{path},
					JOptionPane.YES_NO_OPTION, JOptionPane.ERROR_MESSAGE);
				if (ret == JOptionPane.YES_OPTION)
					IndexWriter.unlock(directory);
			}
			writer = new IndexWriter(directory, analyzer,
				IndexWriter.MaxFieldLength.UNLIMITED);

		}
		catch (IOException e) { e.printStackTrace(); }
	}

	private static String getIndexPath() throws RuntimeException
	{
		String settings = jEdit.getSettingsDirectory();
		if (settings == null || settings.isEmpty())
			throw new RuntimeException("CtagsInterface plugin cannot work without a settings directory.");
		return jEdit.getSettingsDirectory() + File.separator +
			"CtagsInterface" + File.separator + "index";
	}

	public void startActivity()
	{
		synchronized(this)
		{
			writeCount++;
		}
	}

	public void endActivity()
	{
		synchronized(this)
		{
			writeCount--;
			if (writeCount == 0)
			{
				try { writer.commit(); }
				catch (IOException e) { e.printStackTrace(); }
			}
		}
	}

	public void close()
	{
		try { writer.close(); }
		catch (Exception e) { e.printStackTrace(); }
	}

	public void getOrigins(OriginType type, final List<String> origins)
	{
		String query = DOCTYPE_FLD + ":" + ORIGIN_DOC_TYPE + " AND " +
			TYPE_FLD + ":" + type.name;
		runQuery(query, MAX_RESULTS, new DocHandler()
		{
			public void handle(Document doc)
			{
				origins.add(doc.get(ORIGIN_ID_FLD));
			}
		});
	}

	public Vector<String> getOrigins(OriginType type)
	{
		Vector<String> originsList = new Vector<String>();
		getOrigins(type, originsList);
		return originsList;
	}

	public String getOriginsOfFile(String file)
	{
		final StringBuilder sb = new StringBuilder();
		String query = DOCTYPE_FLD + ":" + TAG_DOC_TYPE + " AND " +
			_PATH_FLD + ":" + escape(file);
		runQuery(query, 1, new DocHandler()
		{
			public void handle(Document doc)
			{
				sb.append(doc.get(ORIGIN_FLD));
			}
		});
		return sb.toString();
	}

	public void getFilesOfOrigin(OriginType type, final List<String> filePaths)
	{
		String query = DOCTYPE_FLD + ":" + TAG_DOC_TYPE + " AND " +
			ORIGIN_FLD + ":" + type.name;
		runQuery(query, MAX_RESULTS, new DocHandler()
		{
			public void handle(Document doc)
			{
				if (!filePaths.contains(doc.get(_PATH_FLD)))
					filePaths.add(doc.get(_PATH_FLD));
			}
		});
	}

	public Vector<String> getFilesOfOrigin(OriginType type)
	{
		Vector<String> filePathsList = new Vector<String>();
		getFilesOfOrigin(type, filePathsList);
		return filePathsList;
	}

	public String appendOrigin(String origins, String origin)
	{
		if (origin.length() > origins.length())
			return origins + origin;
		// Check end of origins string
		int index = origins.lastIndexOf(origin);
		if (index >= 0 && index + origin.length() == origins.length())
			return origins;
		// Check middle of origins string
		String originInMiddle = origin + Origin.SEP;
		if (origins.indexOf(originInMiddle) >= 0)
			return origins;
		return origins + origin;
	}

	/**
	 * Completely delete tags from source file
	 * @param path to file
	 */
	public void deleteTagsFromSourceFile(String filePath)
	{
		String s = _PATH_FLD + ":" + escape(filePath);
		deleteQuery(s);
	}

	/**
	 * Delete all tags from a source file of origin >>MISC:temp.
	 * If a tag belongs to multiple origins, only remove the specified origin from it.
	 * @param logger
	 * @param path to file
	 * @param origin
	 */
	public void deleteTagsFromSourceFileOfOrigin(Logger logger, String filePath)
	{
		Origin origin = getOrigin(OriginType.MISC, "temp", true);
		deleteTagsFromSourceFileOfOrigin(logger, filePath, origin);
	}

	/**
	 * Delete all tags from a source file that belong only to the specified origin.
	 * If a tag belongs to multiple origins, only remove the specified origin from it.
	 * @param logger
	 * @param path to file
	 * @param origin
	 */
	public void deleteTagsFromSourceFileOfOrigin(Logger logger, String filePath, Origin origin)
	{
		deleteTagsOfOriginAndFilePath(logger, origin, filePath);
	}

	public void deleteTag(Tag tag)
	{
		String s = _NAME_FLD + ":" + escape(tag.getName()) + " AND " +
					_PATH_FLD + ":" + escape(tag.getFile()) + " AND " +
					PATTERN_FLD + ":" + escape(tag.getPattern());
		deleteQuery(s);
	}

	public void deleteQuery(Query q)
	{
		if (q != null)
		{
			try {
				writer.deleteDocuments(q);
			}
			catch (IOException e) { e.printStackTrace();}
		}
	}

	public void deleteQuery(String s)
	{
		Query q = getQuery(s);
		if (q != null)
		{
			try {
				writer.deleteDocuments(q);
			}
			catch (IOException e) { e.printStackTrace();}
		}
	}

	public void getIdenticalTags(Tag tag, List<Tag> tags)
	{
		StringBuilder q = new StringBuilder(_NAME_FLD + ":" +
			escape(tag.getName()));
		q.append(" AND " + _PATH_FLD + ":" + escape(tag.getFile()));
		Set<String> extensions = tag.getExtensions();
		if (extensions != null)
		{
			for (String s: extensions)
			{
				if (! s.equals(LINE_FLD))
					q.append(" AND " + s + ":" + escape(tag.getExtension(s)));
			}
		}
		queryTags(q.toString(), MAX_RESULTS, tags);
	}

	/*
	 * Delete all tags that belong only to the specified origin. If a tag
	 * belongs to multiple origins, only remove the specified origin from it.
	 */
	public void deleteTagsOfOrigin(Logger logger, final Origin origin)
	{
		deleteTagsOfOriginAndFilePath(logger, origin, null);
	}

	/*
	 * Delete all tags that belong only to the specified origin and source file if set.
	 * If a tag belongs to multiple origins, only remove the specified origin from it.
	 */
	public void deleteTagsOfOriginAndFilePath(Logger logger, final Origin origin, String filePath)
	{
		// File path should only be included when dealing with MISC:temp
		// used as proxy for origin ID
		String filePathStr = "";
		if(filePath!=null) {
			if (origin.toString().equals(">>MISC:temp"))
				filePathStr = " AND " + _PATH_FLD + ":" + filePath;
		}

		startActivity();
		// Delete the tags which belong only to the specified origin.
		// Using _ORIGIN_FLD for a precise match.
		String s = DOCTYPE_FLD + ":" + TAG_DOC_TYPE + " AND " +
			_ORIGIN_FLD + ":" + escape(origin.toString()) + filePathStr;
		Query q = getQuery(s);
		if (q != null)
		{
			try
			{
				writer.deleteDocuments(q);
				writer.commit(); // Tags show up in next query if no commit here
			}
			catch (IOException e) { e.printStackTrace(); }
		}

		// Remove the specified origin from remaining tags.
		// Using ORIGIN_FLD for a substring match.
		s = DOCTYPE_FLD + ":" + TAG_DOC_TYPE + " AND " +
			ORIGIN_FLD + ":" + escape(origin.toString()) + filePathStr;
		runQuery(s, MAX_RESULTS, new DocHandler()
		{
			public void handle(Document doc)
			{
				// Try the end of the string
				String origins = doc.get(ORIGIN_FLD);
				String s = origin.toString();
				int index = origins.lastIndexOf(s);
				if (index < 0)
					return;
				String newValue;
				if (index + s.length() == origins.length())
					newValue = origins.substring(0, index);
				else
				{
					index = origins.indexOf(s + Origin.SEP);
					if (index < 0)
						return;
					newValue = origins.substring(0, index) +
						origins.substring(index + s.length());
				}
				// Create a query for deleting this document, then delete it
				// and re-add it.
				String queryStr = DOCTYPE_FLD + ":" + doc.get(DOCTYPE_FLD) + " AND " +
					_NAME_FLD + ":" + escape(doc.get(_NAME_FLD)) + " AND " +
					_PATH_FLD + ":" + escape(doc.get(_PATH_FLD)) + " AND " +
					LINE_FLD + ":" + doc.get(LINE_FLD);
				Query q = getQuery(queryStr);
				if (q != null)
				{
					try
					{
						writer.deleteDocuments(q);
					}
					catch (IOException e) {
						e.printStackTrace();
					}
				}
				doc.removeField(ORIGIN_FLD);
				doc.removeField(_ORIGIN_FLD);
				addTagOrigins(doc, newValue);
				try { writer.addDocument(doc); }
				catch (IOException e) { e.printStackTrace(); }
			}
		});
		endActivity();
	}

	public static String escape(String s)
	{
		if (s.equals("AND") || s.equals("OR") || s.equals("NOT"))
			return "\"" + s + "\"";
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < s.length(); i++)
		{
			char c = s.charAt(i);
			switch (c)
			{
			case '+': case '-': case '!': case '(': case ')':
			case '{': case '}': case '[': case ']': case '^':
			case '"': case '~': case '*': case '?': case ':':
			case '\\': case ' ':
				sb.append('\\');
				break;
			case '&':
			case '|':
				if (i < s.length() - 1 && s.charAt(i + 1) == c)
					sb.append('\\');
				break;
			}
			sb.append(c);
		}
		return sb.toString();
	}

	// Deletes an origin and all its associated data from the index
	public void deleteOrigin(Logger logger, Origin origin)
	{
		startActivity();
		deleteTagsOfOrigin(logger, origin);
		String s = DOCTYPE_FLD + ":" + ORIGIN_DOC_TYPE + " AND " +
			TYPE_FLD + ":" + origin.type.name + " AND " + ORIGIN_ID_FLD + ":" +
			escape(origin.id);
		Query q = getQuery(s);
		if (q != null)
		{
			try { writer.deleteDocuments(q); writer.optimize(); }
			catch (IOException e) { e.printStackTrace(); }
		}
		endActivity();
	}

	public Origin getOrigin(OriginType type, String id,
		boolean createIfNotExists)
	{
		Origin origin = new Origin(type, id);
		if (! createIfNotExists)
			return origin;
		// Create an origin document if needed
		final boolean b[] = new boolean[1];
		b[0] = false;
		String query = DOCTYPE_FLD + ":" + ORIGIN_DOC_TYPE + " AND " +
			TYPE_FLD + ":" + type.name + " AND " + ORIGIN_ID_FLD + ":" +
			escape(id);
		runQuery(query, 1, new DocHandler() {
			public void handle(Document doc)
			{
				b[0] = true;
			}
		});
		if (! b[0])
		{
			// Create a document for this origin
			startActivity();
			Document doc = new Document();
			doc.add(new Field(DOCTYPE_FLD, ORIGIN_DOC_TYPE, Store.YES, Index.ANALYZED));
			doc.add(new Field(TYPE_FLD, type.name, Store.YES, Index.ANALYZED));
			doc.add(new Field(ORIGIN_ID_FLD, id, Store.YES, Index.ANALYZED));
			try { writer.addDocument(doc); }
			catch (IOException e) { e.printStackTrace(); }
			endActivity();
		}
		return origin;
	}

	public void insertTag(Tag t, String originsStr)
	{
		Document doc = tagToDocument(t, originsStr);
		try { writer.addDocument(doc); }
		catch (Exception e) { e.printStackTrace(); }
	}

	public boolean hasSourceFile(String file)
	{
		final boolean b[] = new boolean[1];
		b[0] = false;
		runQuery(_PATH_FLD + ":" + escape(file), 1, new DocHandler() {
			public void handle(Document doc)
			{
				b[0] = true;
			}
		});
		return b[0];
	}

	public void queryTag(String name, final List<Tag> tags)
	{
		if (tags == null)
			return;
		runQuery(_NAME_FLD + ":" + escape(name), MAX_RESULTS, new DocHandler() {
			public void handle(Document doc)
			{
				Tag tag = documentToTag(doc);
				tags.add(tag);
			}
		});
	}

	public void queryTags(String query, int maxResults, final List<Tag> tags)
	{
		if (tags == null)
			return;
		runQuery(query, maxResults, new DocHandler() {
			public void handle(Document doc)
			{
				Tag tag = documentToTag(doc);
				tags.add(tag);
			}
		});
	}

	private Document tagToDocument(Tag t, String originsStr)
	{
		Document doc = new Document();
		doc.add(new Field(_NAME_FLD, t.getName(), Store.YES, Index.ANALYZED));
		doc.add(new Field(_NAME_LOWERCASE_FLD, t.getName().toLowerCase(),
			Store.YES, Index.ANALYZED));
		doc.add(new Field(PATTERN_FLD, t.getPattern(), Store.YES, Index.ANALYZED));
		doc.add(new Field(PATH_FLD, t.getFile(), Store.NO, Index.ANALYZED));
		doc.add(new Field(_PATH_FLD, t.getFile(), Store.YES, Index.ANALYZED));
		for (String ext: t.getExtensions())
		{
			String val = t.getExtension(ext);
			if (val == null)
				val = "";
			doc.add(new Field(ext, val, Store.YES, Index.ANALYZED));
		}
		addTagOrigins(doc, originsStr);
		doc.add(new Field(DOCTYPE_FLD, TAG_DOC_TYPE, Store.YES, Index.ANALYZED));
		return doc;
	}

	private Tag documentToTag(Document doc)
	{
		Tag tag = new Tag(doc.get(_NAME_FLD), doc.get(_PATH_FLD), doc.get(PATTERN_FLD));
		Hashtable<String, String> extensions = new Hashtable<String, String>();
		for (Fieldable field: doc.getFields())
		{
			if (fixedFields.contains(field.name()))
				continue;
			String val = field.stringValue();
			if (val == null)
				val = "";
			extensions.put(field.name(), val);
		}
		tag.setExtensions(extensions);
		Hashtable<String, String> attachments = new Hashtable<String, String>();
		attachments.put(ORIGIN_FLD, doc.get(ORIGIN_FLD));
		tag.setAttachments(attachments);
		return tag;
	}

	private void addTagOrigins(Document doc, String originsStr)
	{
		doc.add(new Field(ORIGIN_FLD, originsStr, Store.YES, Index.ANALYZED));
		doc.add(new Field(_ORIGIN_FLD, originsStr, Store.NO, Index.ANALYZED));
	}

	/* Various queries */

	public void runQuery(String query, final List<Tag> tags)
	{
		runQuery(query, MAX_RESULTS, tags);
	}

	public void runQuery(String query, int maxResults, final List<Tag> tags)
	{
		runQuery(query, maxResults, new DocHandler()
		{
			public void handle(Document doc)
			{
				tags.add(documentToTag(doc));
			}
		});
	}

	public void runQueryInOrigins(String query, List<Origin> origins,
		int maxResults, final List<Tag> tags)
	{
		if (origins == null || origins.isEmpty())
			return;
		boolean isFirst = true;
		StringBuilder sb = new StringBuilder("(");
		for (Origin origin: origins)
		{
			if (! isFirst)
				sb.append(" OR ");
			String escaped = escape(origin.toString());
			sb.append(_ORIGIN_FLD + ":*" + escaped + " OR " + _ORIGIN_FLD +
				":*" + escaped + Origin.SEP + "*");
			isFirst = false;
		}
		sb.append(")");
		if (query != null && (! query.isEmpty()))
			sb.append(" AND (" + query + ")");
		runQuery(sb.toString(), maxResults, tags);
	}

	// Returns a query for a tag name in a list of specified origins
	// origins: A hash of origin type -> vector of origin names
	// tags: List of tags to be filled in by this query
	public void queryTagInOrigins(String tag, List<Origin> origins,
		final List<Tag> tags)
	{
		queryTagInOrigins(tag, origins, MAX_RESULTS, tags);
	}
	public void queryTagInOrigins(String tag, List<Origin> origins,
		int maxResults, final List<Tag> tags)
	{
		runQueryInOrigins(_NAME_FLD + ":" + escape(tag), origins,
			maxResults, tags);
	}

	public String getTagNameQuery(String name)
	{
		return DOCTYPE_FLD + ":" + TAG_DOC_TYPE + " AND " +
			_NAME_FLD + ":" + escape(name);
	}

	public String getLangNameQuery(String lang) {
		return LANGUAGE + ":" + escape(lang);
	}

	private Query getQuery(String query)
	{
		Log.log(Log.MESSAGE, TagIndex.class, "Parsing query: " + query);
		QueryParser qp = new QueryParser(Version.LUCENE_30, _NAME_FLD, analyzer);
		qp.setAllowLeadingWildcard(true);
		qp.setLowercaseExpandedTerms(false);
		try
		{
			return qp.parse(query);
		}
		catch (Exception e)
		{
			e.printStackTrace();
			Log.log(Log.WARNING, TagIndex.class, "Parsing failed for query: " + query);
		}
		return null;
	}

	public void runQuery(String query, int maxResults, DocHandler handler)
	{
		Query q = getQuery(query);
		if (q == null)
			return;
		try
		{
			Log.log(Log.MESSAGE, TagIndex.class, "Searching query '" + q.toString() + "' started.");
			IndexSearcher searcher = new IndexSearcher(directory, true);
			TopDocs topDocs = searcher.search(q, maxResults);
			Log.log(Log.MESSAGE, TagIndex.class, "Searching query: '" + q.toString() + "' ended.");
			Log.log(Log.MESSAGE, TagIndex.class, "Processing of " + topDocs.scoreDocs.length + " query results started.");
			for (ScoreDoc scoreDoc: topDocs.scoreDocs)
			{
				Document doc = searcher.doc(scoreDoc.doc);
				handler.handle(doc);
			}
			Log.log(Log.MESSAGE, TagIndex.class, "Processing query results ended.");
			Log.log(Log.MESSAGE, TagIndex.class, "Closing searcher started.");
			searcher.close();
			Log.log(Log.MESSAGE, TagIndex.class, "Closing searcher ended.");
		}
		catch (IndexNotFoundException e) { /* ignore */ }
		catch (IOException e) { e.printStackTrace(); }
	}

	public static class Origin
	{
		private static String SEP = ">>";
		public OriginType type;
		public String id;
		public String s;

		public Origin(OriginType type, String id)
		{
			this.type = type;
			this.id = id;
			s = SEP + type + ":" + id;
		}
		public String toString()
		{
			return s;
		}
		public static void fromString(String s, List<Origin> origins)
		{
			int index = s.indexOf(SEP);
			if (index < 0)
				return;
			do
			{
				int nextIndex = s.indexOf(SEP, index + 1);
				String origin;
				if (nextIndex >= 0)
					origin = s.substring(index, nextIndex);
				else
					origin = s.substring(index);
				String [] parts = origin.substring(SEP.length()).split(":", 2);
				origins.add(new Origin(OriginType.valueOf(parts[0]), parts[1]));
				index = nextIndex;
			}
			while (index >= 0);
		}
		public boolean equals(Origin origin)
		{
			return (type == origin.type && id.equals(origin.id));
		}
	}

	public interface DocHandler
	{
		void handle(Document doc);
	}
}
