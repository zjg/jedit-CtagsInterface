package ctagsinterface.main;

import java.util.ArrayList;

import javax.swing.JOptionPane;

import org.gjt.sp.jedit.EditAction;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.jEdit;

import ctagsinterface.options.ActionsOptionPane;
import ctagsinterface.projects.ProjectWatcher;

public class QueryAction extends EditAction {

	private static int MAX_RESULTS = 1000;

	public enum QueryType
	{
		JUMP_TO_TAG("Jump to tag"),
		SEARCH_SUBSTRING("Search substring"),
		SEARCH_PREFIX("Search prefix");

		private QueryType(String txt)
		{
			text = txt;
		}
		public final String text;
	};

	public static final String TAG = "{tag}";
	public static final String PROJECT = "{project}";
	public static final String FILE = "{file}";
	private String query;
	private String desc;
	private QueryType queryType;
	private boolean callImmediately;

	public QueryAction(String name, String query, QueryType queryType,
		boolean showImmediately)
	{
		super(name);
		jEdit.setTemporaryProperty(name + ".label", name);
		this.query = query;
		this.queryType = queryType;
		this.callImmediately = showImmediately;
		desc = getDesc();
	}

	public QueryAction(int index)
	{
		super(jEdit.getProperty(ActionsOptionPane.ACTIONS + index + ".name"));
		jEdit.setTemporaryProperty(getName() + ".label", getName());
		String base= ActionsOptionPane.ACTIONS + index + ".";
		query = jEdit.getProperty(base + "query");
		String queryTypeString = jEdit.getProperty(base + "queryType");
		if (queryTypeString == null || queryTypeString.length() == 0)
		    queryType = QueryType.JUMP_TO_TAG;
		else
		    queryType = QueryType.valueOf(queryTypeString);
		callImmediately = jEdit.getBooleanProperty(base + "callImmediately",
			false);
		desc = getDesc();
	}

	private String getDesc()
	{
		return name + " (" + queryType.text + " - \"" + query + "\")";
	}

	private String fillQueryParameters(View view)
	{
	    ProjectWatcher pvi = CtagsInterfacePlugin.getProjectWatcher();
	    String project = (pvi == null) ? null : pvi.getActiveProject(view);
	    if (project == null && query.contains(PROJECT))
	    {
	        JOptionPane.showMessageDialog(view,
	            "No active project exists");
	        return null;
	    }
	    String s = query;
	    if (project != null)
	        s = s.replace(PROJECT, project);
	    s = s.replace(FILE, view.getBuffer().getPath());
	    if (s.contains(TAG))
	    {
	        String tag = CtagsInterfacePlugin.getDestinationTag(view);
	        if (tag == null)
	        {
	            JOptionPane.showMessageDialog(view,
	            	"No tag selected nor identified at caret");
	            return null;
	        }
	        s = s.replace(TAG, tag);
	    }
	    return s;
	}

	@Override
	public void invoke(View view)
	{
	    String s = fillQueryParameters(view);
	    switch (queryType)
	    {
	    case JUMP_TO_TAG:
	    	ArrayList<Tag> tags = new ArrayList<Tag>();
            CtagsInterfacePlugin.getIndex().queryTags(s, MAX_RESULTS, tags);
	        CtagsInterfacePlugin.jumpToTags(view, tags);
	    break;
	    case SEARCH_PREFIX:
	        new QuickSearchTagDialog(view, QuickSearchTagDialog.Mode.PREFIX, name, s, callImmediately);
	        break;
	    case SEARCH_SUBSTRING:
	        new QuickSearchTagDialog(view, QuickSearchTagDialog.Mode.SUBSTRING, name, s, callImmediately);
	        break;
	    }
	}

	public String toString()
	{
		return desc;
	}

	public String getQuery()
	{
		return query;
	}

	public QueryType getQueryType()
	{
        return queryType;
    }

	public boolean isShowImmediately()
	{
        return callImmediately;
    }

	public void save(int index)
	{
		String base = ActionsOptionPane.ACTIONS + index + ".";
		jEdit.setProperty(base + "name", getName());
		jEdit.setProperty(base + "query", query);
		jEdit.setProperty(base + "queryType", queryType.toString());
		jEdit.setBooleanProperty(base + "callImmediately", callImmediately);
	}
}
