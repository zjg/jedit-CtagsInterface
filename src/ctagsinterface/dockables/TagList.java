package ctagsinterface.dockables;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.TreeSet;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.gui.DefaultFocusComponent;

import ctagsinterface.index.TagIndex;
import ctagsinterface.index.TagIndex.Origin;
import ctagsinterface.index.TagIndex.OriginType;
import ctagsinterface.main.CtagsInterfacePlugin;
import ctagsinterface.main.Tag;
import ctagsinterface.main.TagListFilterMenu;
import ctagsinterface.main.TagListFilterMenu.TagListModelHandler;

@SuppressWarnings("serial")
public class TagList extends JPanel implements DefaultFocusComponent
{
	View view;
	JList tags;
	DefaultListModel tagModel;
	TagListFilterMenu menu;
	TagListDockableModelHandler handler;
	static String [] extensionOrder = new String [] {
		"class", "struct", "access" 
	};

	public class TagListDockableModelHandler implements TagListModelHandler
	{
		public void clear()
		{
			tagModel.removeAllElements();
		}
		public void add(Tag t)
		{
			tagModel.addElement(t);
		}
		public void done()
		{
			if (tagModel.size() == 1)
				jumpTo(0);
		}
	}

	public TagList(View view)
	{
		super(new BorderLayout());
		this.view = view;
		handler = new TagListDockableModelHandler();
		menu = new TagListFilterMenu(handler);
		add(menu, BorderLayout.NORTH);
		tagModel = new DefaultListModel();
		tags = new JList(tagModel);
		add(new JScrollPane(tags), BorderLayout.CENTER);
		tags.setCellRenderer(new TagListCellRenderer());
		tags.addMouseListener(new MouseAdapter()
		{
			public void mouseClicked(MouseEvent me)
			{
				jumpTo(tags.getSelectedIndex());
			}
		});
		tags.addKeyListener(new KeyAdapter()
		{
			public void keyTyped(KeyEvent ke)
			{
				char c = ke.getKeyChar();
				int index = -1;
				if (c == ' ')
					index = tags.getSelectedIndex();
				else if (c >= '1' && c <= '9')
					index = c - '1';
				if (index >= 0)
				{
					ke.consume();
					jumpTo(index);
				}
			}
			//keyTyped events don't provide a key code
			public void keyPressed(KeyEvent ke)
			{
				if (ke.getKeyCode() == KeyEvent.VK_ENTER)
				{
					ke.consume();
					jumpTo(tags.getSelectedIndex());
				}
			}
		});
		setTags(null);
	}
	
	protected void jumpTo(int selectedIndex)
	{
		Tag tag = (Tag) tagModel.getElementAt(selectedIndex);
		CtagsInterfacePlugin.jumpToTag(view, tag);
		if (view != null)
			view.getTextArea().requestFocus(); 
	}

	public void setTags(List<Tag> tags)
	{
		menu.setTags(tags);
	}

	public void focusOnDefaultComponent()
	{
		tags.requestFocus();
	}

	private final class TagListCellRenderer extends DefaultListCellRenderer
	{
		public Component getListCellRendererComponent(JList list, Object value,
			int index, boolean isSelected, boolean cellHasFocus)
		{
			JLabel l = (JLabel) super.getListCellRendererComponent(list,
				value, index, isSelected, cellHasFocus);
			Tag tag = (Tag) tagModel.getElementAt(index);
			l.setText(getHtmlText(tag, index));
			l.setFont(new Font("Monospaced", Font.PLAIN, 12));
			ImageIcon icon = tag.getIcon();
			if (icon != null)
				l.setIcon(icon);
			l.setBorder(BorderFactory.createLoweredBevelBorder());
			return l;
		}

		private String getHtmlText(Tag tag, int index)
		{
			StringBuffer s = new StringBuffer("<html>");
			s.append(index + 1);
			s.append(": <b>");
			s.append(tag.getQualifiedName());
			s.append("</b>  ");
			String originStr = tag.getAttachment(TagIndex.ORIGIN_FLD);
			ArrayList<Origin> origins = new ArrayList<Origin>();
			Origin.fromString(originStr, origins);
			for (Origin origin: origins)
			{
				if (origin != null && origin.type == OriginType.PROJECT)
				{
					String project = origin.id;
					if (project != null && project.length() > 0)
					{
						s.append("(<i>");
						s.append(project);
						s.append("</i>)  ");
					}
				}
			}
			s.append(tag.getFile());
			s.append((tag.getLine() >= 0) ? ":" + tag.getLine() : "");
			s.append("<br>");
			s.append(depattern(tag.getPattern()));
			s.append("<br>");
			Vector<String> extOrder = new Vector<String>();
			for (int i = 0; i < extensionOrder.length; i++)
			{
				if (tag.getExtension(extensionOrder[i]) != null)
					extOrder.add(extensionOrder[i]);
			}
			TreeSet<String> extensions =
				new TreeSet<String>(tag.getExtensions());
			Iterator<String> it = extensions.iterator();
			while (it.hasNext())
			{
				String extension = (String) it.next();
				if (extension.equals("line") || extOrder.contains(extension))
					continue;
				extOrder.add(extension);
			}
			boolean first = true;
			for (int i = 0; i < extOrder.size(); i++)
			{
				if (! first)
					s.append(",  ");
				first = false;
				String extension = extOrder.get(i);
				s.append(extOrder.get(i));
				s.append(": ");
				s.append(tag.getExtension(extension));
			}
			return s.toString();
		}

		private Object depattern(String pattern)
		{
			if (pattern.startsWith("/^"))
				pattern = pattern.substring(2);
			if (pattern.endsWith("$/"))
				pattern = pattern.substring(0, pattern.length() - 2);
			return pattern;
		}
	}

}
