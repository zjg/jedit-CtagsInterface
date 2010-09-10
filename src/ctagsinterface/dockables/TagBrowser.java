package ctagsinterface.dockables;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Comparator;
import java.util.Vector;

import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import org.gjt.sp.jedit.View;
import org.gjt.sp.util.ThreadUtilities;

import ctagsinterface.main.CtagsInterfacePlugin;
import ctagsinterface.main.Tag;

@SuppressWarnings("serial")
public class TagBrowser extends JPanel
{
	private View view;
	private JTree tree;
	private DefaultTreeModel model;
	private DefaultMutableTreeNode root;
	private IMapper mapper;
	private ISorter sorter;
	private JRadioButton kind, namespace;
	private Runnable repaintTree;
	private Runnable updateTree;
	private boolean repaintTreeMarked;
	private JCheckBox caseSensitiveSorting;

	public TagBrowser(View view)
	{
		this.view = view;
		setLayout(new BorderLayout());
		root = new DefaultMutableTreeNode();
		model = new DefaultTreeModel(root);
		tree = new JTree(model);
		tree.setRootVisible(false);
		tree.setShowsRootHandles(true);
		tree.setCellRenderer(new TagTreeCellRenderer());
		tree.addMouseListener(new MouseAdapter()
		{

			@Override
			public void mouseClicked(MouseEvent e)
			{
				TreePath tp = tree.getPathForLocation(e.getX(), e.getY());
				if (tp == null)
					return;
				DefaultMutableTreeNode node = (DefaultMutableTreeNode)
					tp.getLastPathComponent();
				if (node.getUserObject() instanceof Tag)
				{
					Tag t = (Tag) node.getUserObject();
					CtagsInterfacePlugin.jumpToTag(TagBrowser.this.view, t);
				}
				super.mouseClicked(e);
			}
		});
		add(new JScrollPane(tree), BorderLayout.CENTER);
		JPanel top = new JPanel();
		add(top, BorderLayout.NORTH);
		JPanel mapPanel = new JPanel();
		top.add(mapPanel);
		mapPanel.add(new JLabel("Group by:"));
		kind = new JRadioButton("Kind");
		mapPanel.add(kind);
		namespace = new JRadioButton("Namespace");
		mapPanel.add(namespace);
		ButtonGroup bg = new ButtonGroup();
		bg.add(kind);
		bg.add(namespace);
		kind.setSelected(true);
		JPanel sortPanel = new JPanel();
		top.add(sortPanel);
		caseSensitiveSorting = new JCheckBox("Case-sensitive sorting");
		sortPanel.add(caseSensitiveSorting);
		JButton update = new JButton("Refresh");
		top.add(update);
		ActionListener listener = new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				populate();
			}
		};
		kind.addActionListener(listener);
		namespace.addActionListener(listener);
		caseSensitiveSorting.addActionListener(listener);
		update.addActionListener(listener);
		repaintTree = new Runnable()
		{
			public void run()
			{
				synchronized(this)
				{
					repaintTreeMarked = false;
					model.nodeStructureChanged(root);
				}
			}
		};
		updateTree = new Runnable()
		{
			public void run()
			{
				mapper = kind.isSelected() ? new KindMapper() :
					new NamespaceMapper();
				sorter = new NameSorter(caseSensitiveSorting.isSelected());
				root.removeAllChildren();
				Vector<Tag> tags = CtagsInterfacePlugin.runScopedQuery(
					TagBrowser.this.view, "doctype:tag", 1000000);
				Vector<Object> path = new Vector<Object>();
				for (Tag t: tags)
					addToTree(t, path);
			}
		};
	}
	public void populate()
	{
		repaintTreeMarked = false;
		ThreadUtilities.runInBackground(updateTree);
	}
	public void addToTree(Tag t, Vector<Object> path)
	{
		if (t.getName().equals("GetPid"))
			path.clear();
		mapper.getPath(t, path);
		DefaultMutableTreeNode current = root;
		synchronized(this)
		{
			for (Object o: path)
				current = addChild(current, o);
			if (! repaintTreeMarked)
			{
				repaintTreeMarked = true;
				ThreadUtilities.runInDispatchThread(repaintTree);
			}
		}
	}
	private DefaultMutableTreeNode addChild(
		DefaultMutableTreeNode parent, Object childData)
	{
		int low = 0, high = parent.getChildCount() - 1, index = -1;
		while (high >= low)
		{
			index = (low + high) / 2;
			DefaultMutableTreeNode child =
				(DefaultMutableTreeNode) parent.getChildAt(index);
			Object data = child.getUserObject();
			int c = sorter.compare(data, childData);
			if (c < 0)
				low = index + 1;
			else if (c > 0)
				high = index - 1;
			else if (data instanceof String)
			{
				if (childData instanceof Tag)
					child.setUserObject(childData);
				return child;
			}
			else if (childData instanceof Tag)
				break;
			else
				return child;
		}
		if (low > index)
			index++;
		DefaultMutableTreeNode child = new DefaultMutableTreeNode(childData);
		parent.insert(child, index);
		return child;
	}

	private interface IMapper
	{
		// Set the given 'path' vector to the path of tag 't'.
		void getPath(Tag t, Vector<Object> path);
	}

	private interface ISorter extends Comparator<Object>
	{
	}

	private static class NamespaceMapper implements IMapper
	{
		private static final String GLOBAL_SCOPE = "<< Global (no namespace) >>";
		private static final String [] Keywords = {
			"namespace", "class", "union", "struct", "enum"
		};
		private String separator = "::|\\.";
		public void getPath(Tag tag, Vector<Object> path)
		{
			path.clear();
			for (String keyword: Keywords)
			{
				String ns = tag.getExtension(keyword);
				if (ns != null)
				{
					String [] parts = ns.split(separator);
					for (String part: parts)
						path.add(part);
					break;
				}
			}
			if (path.isEmpty())
				path.add(GLOBAL_SCOPE);
			path.add(tag);
		}
	}
	private static class KindMapper implements IMapper
	{
		public void getPath(Tag t, Vector<Object> path)
		{
			path.clear();
			path.add(t.getKind());
			path.add(t);
		}
	}

	private static class NameSorter implements ISorter
	{
		boolean caseSensitive;
		public NameSorter(boolean caseSensitive)
		{
			this.caseSensitive = caseSensitive;
		}
		public int compare(Object o1, Object o2)
		{
			String s1 = (o1 instanceof Tag) ? ((Tag)o1).getName() : o1.toString();
			String s2 = (o2 instanceof Tag) ? ((Tag)o2).getName() : o2.toString();
			if (! caseSensitive)
			{
				s1 = s1.toLowerCase();
				s2 = s2.toLowerCase();
			}
			return s1.compareTo(s2);
		}
	}

	private class TagTreeCellRenderer extends DefaultTreeCellRenderer
	{
		
		@Override
	    public Component getTreeCellRendererComponent(JTree tree,
	    	Object value, boolean sel, boolean expanded, boolean leaf,
	    	int row, boolean hasFocus)
		{
			super.getTreeCellRendererComponent(tree, value, sel, expanded,
				leaf, row, hasFocus);
			setOpenIcon(null);
			setClosedIcon(null);
			if (value instanceof DefaultMutableTreeNode)
			{
				DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
				Object obj = node.getUserObject();
				if (obj instanceof Tag)
				{
					Tag t = (Tag) obj;
					setText(t.getName());
					ImageIcon icon = t.getIcon();
					setIcon(icon);
				}
			}
			return this;
		}
	}
}
