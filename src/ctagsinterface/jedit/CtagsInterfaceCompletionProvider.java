package ctagsinterface.jedit;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Vector;


import org.gjt.sp.jedit.Mode;
import org.gjt.sp.jedit.View;

import completion.service.CompletionCandidate;
import completion.service.CompletionProvider;
import ctagsinterface.index.TagIndex;
import ctagsinterface.main.CtagsInterfacePlugin;
import ctagsinterface.main.Tag;

public class CtagsInterfaceCompletionProvider implements CompletionProvider {

	@Override
	public List<CompletionCandidate> getCompletionCandidates(View view) {
		String prefix = CtagsInterfacePlugin.getCompletionPrefix(view);
		if (prefix == null)
			return null;
		final Vector<Tag> tags = getCompletions(view, prefix);
		if (tags == null || tags.isEmpty())
			return null;
		List<CompletionCandidate> candidates = new ArrayList<CompletionCandidate>();
		for (Tag t: tags)
			candidates.add(new CtagsCompletionCandidate(t));
		return candidates;
	}

	@Override
	public Set<Mode> restrictToModes() {
		return null;
	}

	private Vector<Tag> getCompletions(View view, String prefix)
	{
		String q = TagIndex._NAME_FLD + ":" + prefix + "*";
		return CtagsInterfacePlugin.runScopedQuery(view, q);
	}

}
