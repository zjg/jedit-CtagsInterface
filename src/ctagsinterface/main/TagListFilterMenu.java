package ctagsinterface.main;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Vector;

import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;

@SuppressWarnings("serial")
public class TagListFilterMenu extends JMenuBar
{
	private TagListModelHandler handler;
	static final String MISSING_EXTENSION = "<none>";

	public interface TagListModelHandler
	{
		void clear();
		void add(Tag t);
		void done();
	}

	public TagListFilterMenu(TagListModelHandler handler)
	{
		this.handler = handler;
	}
	public void setTags(final List<Tag> tags)
	{
		if (handler == null)
			return;
		removeAll();
		handler.clear();
		if (tags == null)
			return;
		HashMap<String, HashSet<String>> menus =
			new HashMap<String, HashSet<String>>();
		for (int i = 0; i < tags.size(); i++)
		{
			Tag tag = tags.get(i);
			handler.add(tag);
			Vector<String> missingExtensions = new Vector<String>(menus.keySet());
			for (String ext: tag.getExtensions())
			{
				missingExtensions.remove(ext);
				HashSet<String> keys = menus.get(ext);
				if (keys == null)
				{
					keys = new HashSet<String>();
					menus.put(ext, keys);
					if (i > 0) // Previous tags did not have this extension
						keys.add(MISSING_EXTENSION);
				}
				keys.add(tag.getExtension(ext));
			}
			// Add a <missing extension> item to menus for missing extensions
			for (String missing: missingExtensions)
			{
				HashSet<String> keys = menus.get(missing);
				if (keys == null)
					continue;
				keys.add(MISSING_EXTENSION);
			}
		}
		handler.done();
		Vector<String> keys = new Vector<String>(menus.keySet());
		Collections.sort(keys);
		for (final String key: keys)
		{
			if (menus.get(key).size() < 2)
				continue;	// Avoid redundant menus
			JMenu m = new JMenu(key);
			add(m);
			Vector<String> values = new Vector<String>(menus.get(key));
			Collections.sort(values);
			for (final String value: values)
			{
				JMenuItem item = new JMenuItem(value);
				m.add(item);
				item.addActionListener(new ActionListener()
				{
					public void actionPerformed(ActionEvent e)
					{
						handler.clear();
						for (Tag t: tags)
						{
							String ext = t.getExtension(key);
							if (ext == null)
								ext = MISSING_EXTENSION;
							if (value.equals(ext))
								handler.add(t);
						}
						handler.done();
					}
				});
			}
		}
		validate();
		repaint();
	}
}
