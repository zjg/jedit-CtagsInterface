package ctagsinterface.main;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JWindow;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.gjt.sp.jedit.GUIUtilities;
import org.gjt.sp.jedit.View;

import ctagsinterface.index.TagIndex;
import ctagsinterface.main.TagListFilterMenu.TagListModelHandler;

@SuppressWarnings("serial")
public class QuickSearchTagDialog extends JDialog {

	enum Mode {
		SUBSTRING,
		PREFIX
	};
	private Mode mode;
	private JTextField name;
	private JList tags;
	private DefaultListModel model;
	private View view;
	private Timer filterTimer;
	private String query;
	private boolean showImmediately;
	private JCheckBox caseSensitive;
	private JCheckBox wholeWord;
	private TagListFilterMenu menu;
	private QuickSearchTagListModelHandler handler;

	/** This window will contains the scroll with the items. */
	final JWindow window = new JWindow(this);

	public class QuickSearchTagListModelHandler implements TagListModelHandler
	{
		public void clear()
		{
			model.removeAllElements();
		}
		public void add(Tag t)
		{
			model.addElement(new QuickSearchTag(t));
		}
		public void done()
		{
			if (model.getSize() == 1)
			{
				jumpTo((QuickSearchTag) model.get(0));
				model.removeAllElements();
			}
		}
	}

	public QuickSearchTagDialog(View view, Mode mode)
	{
		this(view, mode, "Search tag", null, false);
	}

	public QuickSearchTagDialog(View view, Mode mode, String title,
		String query, boolean showImmediately)
	{
		super(view, title, false);
		this.view = view;
		this.mode = mode;
		this.query = query;
		this.showImmediately = showImmediately;
		JPanel p = new JPanel();
		p.add(new JLabel("Type part of the tag name:"));
		name = new JTextField(30);
		p.add(name);
		ActionListener refilter = new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				applyFilter();
			}
		};
		caseSensitive = new JCheckBox("Case-sensitive", false);
		p.add(caseSensitive);
		caseSensitive.addActionListener(refilter);
		wholeWord = new JCheckBox("Whole-word", false);
		p.add(wholeWord);
		wholeWord.addActionListener(refilter);
		add(p, BorderLayout.NORTH);
		JPanel listPanel = new JPanel();
		listPanel.setLayout(new BorderLayout());
		handler = new QuickSearchTagListModelHandler();
		menu = new TagListFilterMenu(handler);
		listPanel.add(menu, BorderLayout.NORTH);
		listPanel.setBorder(BorderFactory.createEtchedBorder());
		model = new DefaultListModel();
		tags = new JList(model);
		tags.setCellRenderer(new TagListCellRenderer());
		listPanel.add(new JScrollPane(tags), BorderLayout.CENTER);
		window.setContentPane(listPanel);
		name.getDocument().addDocumentListener(new DocumentListener() {
			public void changedUpdate(DocumentEvent e) {
				setFilter();
			}
			public void insertUpdate(DocumentEvent e) {
				setFilter();
			}
			public void removeUpdate(DocumentEvent e) {
				setFilter();
			}
		});
		name.addKeyListener(new KeyAdapter()
			{
				public void keyPressed(KeyEvent e) {
					if (handledByList(e)) {
						tags.dispatchEvent(e);
					} else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
						setVisible(false);
					} else if (e.getKeyCode() == KeyEvent.VK_ENTER) {
						jumpToSelected();
					}
				}
			}
		);
		tags.addMouseListener(new MouseAdapter() {
			public void mouseClicked(MouseEvent arg0) {
				jumpToSelected();
			}
		});
		tags.addKeyListener(new KeyAdapter() {
			public void keyTyped(KeyEvent e) {
				name.dispatchEvent(e);
			}

			public void keyPressed(KeyEvent e) {
				if (!handledByList(e)) {
					name.dispatchEvent(e);
				}
				else if (e.getKeyCode() == KeyEvent.VK_ENTER) {
					e.consume();
					jumpToSelected();
				}
			}
		});
		filterTimer = new Timer(500, new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				applyFilter();
			}
		});
		filterTimer.setRepeats(false);
		pack();
		setLocationRelativeTo(view);
		setVisible(true);
		if (showImmediately)
			applyFilter();
	}

	private void jumpToSelected() {
		QuickSearchTag t = (QuickSearchTag) tags.getSelectedValue();
		jumpTo(t);
	}

	private void jumpTo(QuickSearchTag t) {
		if (t != null)
			CtagsInterfacePlugin.jumpTo(view, t.file, t.line);
		setVisible(false);
		dispose();
	}

	private void setFilter() {
		if (filterTimer.isRunning())
			filterTimer.restart();
		else
			filterTimer.start();
	}

	private void applyFilter()
	{
		final String input = caseSensitive.isSelected() ? name.getText():
			name.getText().toLowerCase();
		if (showImmediately || (! input.isEmpty()))
		{
			String s = (query == null) ? "" : query;
			if (! input.isEmpty())
			{
				if (s.length() > 0)
					s = s + " AND ";
				String field = caseSensitive.isSelected() ?
					TagIndex._NAME_FLD : TagIndex._NAME_LOWERCASE_FLD;
				String value = (wholeWord.isSelected()) ? input :
					(mode == Mode.SUBSTRING ? "*" : "") + input + "*";
				s = s + field + ":" + value;
			}
			Vector<Tag> tags = CtagsInterfacePlugin.runScopedQuery(view, s);
			menu.setTags(tags);
		}
		if (model.isEmpty())
			window.setVisible(false);
		else
		{
			tags.setVisibleRowCount(Math.min(10, model.size()));
			window.pack();
			window.setVisible(true);
		}
	}

	private static boolean handledByList(KeyEvent e) {
		return e.getKeyCode() == KeyEvent.VK_DOWN ||
		e.getKeyCode() == KeyEvent.VK_UP ||
		e.getKeyCode() == KeyEvent.VK_PAGE_DOWN ||
		e.getKeyCode() == KeyEvent.VK_PAGE_UP;
	}

	public void setVisible(boolean b) {
		if (b) 	{
			Rectangle bounds = getBounds();
			window.setLocation(bounds.x, bounds.y + bounds.height);
			GUIUtilities.requestFocus(this, name);
			window.setVisible(false);	// Initially hide the tag list window
		}
		super.setVisible(b);
	}

	private static class QuickSearchTag
	{
		String file;
		int line;
		String name;
		String desc;
		String kind;
		public QuickSearchTag(Tag t)
		{
			StringBuffer text = new StringBuffer();
			name = t.getName();
			text.append(name);
			kind = t.getKind();
			if (kind != null)
				text.append(" (" + kind + ")");
			file = t.getFile();
			line = t.getLine();
			desc = text.toString();
			if (isValid())
				desc = desc + "   [" + file + ":" + line + "]";
		}
		public boolean isValid()
		{
			return (desc.length() > 0 && file != null && line >= 0);
		}
		public String toString()
		{
			return desc;
		}
		public ImageIcon getIcon()
		{
			return KindIconProvider.getIcon(kind);
		}
	}
	private class TagListCellRenderer extends DefaultListCellRenderer {

		@Override
		public Component getListCellRendererComponent(JList list, Object value,
				int index, boolean isSelected, boolean cellHasFocus) {
			JLabel l = (JLabel) super.getListCellRendererComponent(
				list, value, index, isSelected, cellHasFocus);
			if (value instanceof QuickSearchTag) {
				ImageIcon icon = ((QuickSearchTag)value).getIcon();
				if (icon != null)
					l.setIcon(icon);
			}
			return l;
		}

	}
}
