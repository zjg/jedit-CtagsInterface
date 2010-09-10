package ctagsinterface.dockables;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.Vector;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.Timer;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.gjt.sp.jedit.EditBus;
import org.gjt.sp.jedit.EditPane;
import org.gjt.sp.jedit.MiscUtilities;
import org.gjt.sp.jedit.Mode;
import org.gjt.sp.jedit.Registers;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.jEdit;
import org.gjt.sp.jedit.EditBus.EBHandler;
import org.gjt.sp.jedit.buffer.JEditBuffer;
import org.gjt.sp.jedit.gui.DefaultFocusComponent;
import org.gjt.sp.jedit.io.VFSManager;
import org.gjt.sp.jedit.msg.EditPaneUpdate;
import org.gjt.sp.jedit.msg.PropertiesChanged;
import org.gjt.sp.jedit.msg.ViewUpdate;
import org.gjt.sp.jedit.syntax.ModeProvider;
import org.gjt.sp.jedit.textarea.JEditEmbeddedTextArea;
import org.gjt.sp.jedit.textarea.JEditTextArea;
import org.gjt.sp.jedit.textarea.TextArea;

import ctagsinterface.main.CtagsInterfacePlugin;
import ctagsinterface.main.Tag;
import ctagsinterface.options.GeneralOptionPane;

@SuppressWarnings("serial")
public class Preview extends JPanel implements DefaultFocusComponent,
	CaretListener, ListSelectionListener
{

	static public final String MESSAGE = CtagsInterfacePlugin.MESSAGE;
	View view;
	JList tags;
	DefaultListModel tagModel;
	TextArea text;
	boolean first = true;
	String file;
	Timer timer;
	Set<JEditTextArea> tracking;
	private JCheckBox wrap;
	private JCheckBox followCaret;
	private JPanel toolbar;
	private JPanel textPanel;
	private boolean toolbarShown;
	private JSplitPane split;
	
	public Preview(final View view) {
		super(new BorderLayout());
		this.view = view;
		timer = null;
		tracking = new HashSet<JEditTextArea>();
		file = null;
		tagModel = new DefaultListModel();
		tags = new JList(tagModel);
		tags.setCellRenderer(new TagListCellRenderer());
		tags.setVisibleRowCount(4);
		tags.addListSelectionListener(this);
		tags.addMouseListener(new MouseAdapter() {
			public void mouseClicked(MouseEvent me) {
				if (me.getButton() == MouseEvent.BUTTON3) {
					final int index = tags.locationToIndex(me.getPoint());
					if (index < 0)
						return;
					final Tag t = (Tag) tagModel.get(index);
					JPopupMenu menu = new JPopupMenu();
					menu.add(new AbstractAction() {
						public Object getValue(String key) {
							if (key.equals(Action.NAME))
								return "Copy absolute path to clipboard";
							return super.getValue(key);
						}
						public void actionPerformed(ActionEvent e) {
							Registers.setRegister('$', t.getFile());
						}
					});
					menu.show(view, me.getXOnScreen(), me.getYOnScreen());
					return;
				}
				if (me.getClickCount() < 2 || tags.getSelectedIndex() < 0)
					return;
				Tag t = (Tag) tagModel.getElementAt(tags.getSelectedIndex());
				CtagsInterfacePlugin.jumpToTag(Preview.this.view, t);
			}
		});
		textPanel = new JPanel();
		textPanel.setLayout(new BorderLayout());
		toolbarShown = false;
		toolbar = new JPanel();
		followCaret = new JCheckBox("Follow caret", true);
		followCaret.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				setCaretTracking(Preview.this.view.getTextArea(), followCaret.isSelected());
			}
		});
		toolbar.add(followCaret);
		wrap = new JCheckBox("Soft wrap", GeneralOptionPane.getPreviewWrap());
		wrap.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				GeneralOptionPane.setPreviewWrap(wrap.isSelected());
				propertiesChanged();
			}
		});
		toolbar.add(wrap);
		text = new PreviewTextArea();
        text.getBuffer().setProperty("folding","explicit");
		textPanel.add(text, BorderLayout.CENTER);
		textPanel.add(text, BorderLayout.CENTER);
		EditPane.initPainter(text.getPainter());
		text.getPainter().addMouseListener(new MouseAdapter() {
			public void mouseClicked(MouseEvent me) {
				if (me.getClickCount() == 2 && file != null) {
					CtagsInterfacePlugin.jumpToOffset(Preview.this.view, file,
						text.getCaretPosition());
				}
			}
		});
		text.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				if (((e.getModifiersEx() & KeyEvent.CTRL_DOWN_MASK) != 0) &&
					(e.getKeyCode() == KeyEvent.VK_C))
				{
					copyPreviewSelection();
					e.consume();
				}
			}
		});
		propertiesChanged();
		text.setMinimumSize(new Dimension(150, 50));
		split = new JSplitPane(getSplitOrientation(),
			new JScrollPane(tags), textPanel);
		split.setOneTouchExpandable(true);
		split.setDividerLocation(100);
		add(split, BorderLayout.CENTER);
		EditBus.addToBus(this);
		this.addHierarchyListener(new HierarchyListener() {
			public void hierarchyChanged(HierarchyEvent e) {
				if ((e.getChangeFlags() & HierarchyEvent.DISPLAYABILITY_CHANGED) > 0)
					setCaretTracking(Preview.this.view.getTextArea(), false);	
			}
		});
		this.addComponentListener(new ComponentListener() {
			public void componentHidden(ComponentEvent arg0) {
				updateCaretListenerState();
			}
			public void componentMoved(ComponentEvent arg0) {
			}
			public void componentResized(ComponentEvent arg0) {
				updateCaretListenerState();
			}
			public void componentShown(ComponentEvent arg0) {
				updateCaretListenerState();
			}
		});
	}

	private void copyPreviewSelection() {
		Registers.copy(text, '$');
	}
	
	private void updateCaretListenerState() {
		boolean visible = isVisible() && getWidth() > 0 && getHeight() > 0; 
		if (visible) {
			if  (followCaret.isSelected())
				setCaretTracking(view.getTextArea(), true);
		}
		else {
			Vector<JEditTextArea> textAreas = new Vector<JEditTextArea>();
			textAreas.addAll(tracking);
			Iterator<JEditTextArea> it = textAreas.iterator();
			while (it.hasNext())
				setCaretTracking(it.next(), false);
		}
	}

	private void setCaretTracking(JEditTextArea textArea, boolean track) {
		if (track)
			caretUpdate(null);
		if (tracking.contains(textArea) == track)
			return;
		if (track) {
			tracking.add(textArea);
			textArea.addCaretListener(this);
		} else {
			tracking.remove(textArea);
			textArea.removeCaretListener(this);	
		}
	}
	
	private int getSplitOrientation() {
		return GeneralOptionPane.getPreviewVerticalSplit() ?
			JSplitPane.VERTICAL_SPLIT : JSplitPane.HORIZONTAL_SPLIT;
	}
	
	private void propertiesChanged() {
		if (split != null)
			split.setOrientation(getSplitOrientation());
		if (GeneralOptionPane.getPreviewToolbar() != toolbarShown) {
			toolbarShown = GeneralOptionPane.getPreviewToolbar();
			if (toolbarShown)
				textPanel.add(toolbar, BorderLayout.NORTH);
			else
				textPanel.remove(toolbar);
		}
		String wrap;
		if (GeneralOptionPane.getPreviewWrap())
			wrap = "soft";
		else
			wrap = "none";
		text.getBuffer().setProperty("wrap", wrap);
		EditPane.initPainter(text.getPainter());
	}
	public void previewTag() {
		String name = null;
		try {
			name = CtagsInterfacePlugin.getDestinationTag(view);
		} catch (Exception e) {
			return;
		}
		if (name == null)
			return;
		VFSManager.runInWorkThread(new QueryTag(name));
	}
	
	public void caretUpdate(CaretEvent e) {
		int delay = GeneralOptionPane.getPreviewDelay(); 
		if (delay > 0) {
			if (timer == null)
			{
				timer = new Timer(delay, new ActionListener() {
					public void actionPerformed(ActionEvent arg0) {
						previewTag();
					}
				});
				timer.setRepeats(false);
				timer.start();
			}
			else
				timer.restart();
		}
		else
			previewTag();
	}
	
	public void valueChanged(ListSelectionEvent e) {
		int index = tags.getSelectedIndex();
		if (index < 0)
			return;
		Tag t = (Tag) tagModel.getElementAt(index);
		VFSManager.runInWorkThread(new PreviewBufferLoader(t));
	}

	public void focusOnDefaultComponent() {
		tags.requestFocus();
	}
	
	static public String getContents(String path) {
		StringBuffer contents = new StringBuffer();
		String ret = null;
		BufferedReader input = null;
		try {
			input = new BufferedReader(new FileReader(path));
			String line = null;
			while ((line = input.readLine()) != null) {
				contents.append(line);
				contents.append(System.getProperty("line.separator"));
			}
			ret = contents.toString();
		}
		catch (IOException ex) {
			//ex.printStackTrace();
		}
		finally {
			try {
				if (input!= null)
					input.close();
			}
			catch (IOException ex) {
				//ex.printStackTrace();
			}
		}
		return ret;
	}

	@EBHandler
	public void handlePropertiesChanged(PropertiesChanged msg)
	{
		propertiesChanged();
	}
	@EBHandler
	public void handleViewUpdate(ViewUpdate msg)
	{
		if ((msg.getView() == view) &&
			(msg.getWhat() == ViewUpdate.EDIT_PANE_CHANGED))
		{
			updateCaretListenerState();
		}
	}
	@EBHandler
	public void handleEditPaneUpdate(EditPaneUpdate msg)
	{
		if (msg.getWhat() == EditPaneUpdate.DESTROYED) {
			JEditTextArea textArea = msg.getEditPane().getTextArea(); 
			if (tracking.contains(textArea))
				setCaretTracking(textArea, false);
		}
	}

	private final class TagListCellRenderer extends DefaultListCellRenderer {
		//private Font tagListFont = new Font("Monospaced", Font.PLAIN, 12);
        
		public Component getListCellRendererComponent(JList list, Object value,
				int index, boolean isSelected, boolean cellHasFocus) {
			JLabel l = (JLabel) super.getListCellRendererComponent(list, value, index,
				isSelected, cellHasFocus);
			Tag tag = (Tag) tagModel.getElementAt(index);
			//l.setFont(tagListFont );
			l.setText(getText(tag, false));
			l.setToolTipText(getText(tag, true));
			ImageIcon icon = tag.getIcon();
			if (icon != null)
				l.setIcon(icon);
			return l;
		}
		
		/**
		 * 
		 * @param tag
		 * @param full if true, include full pathname (tooltip). if false, return short format (text)
		 * @return
		 */
		String getText(Tag tag, boolean full) {
			StringBuffer s = new StringBuffer();
			s.append(tag.getName());
			String signature = tag.getExtension("signature");
			if (signature != null && signature.length() > 0)
				s.append(signature);
			s.append("   ");
			int line = tag.getLine();
			if (full) {
				s.append(tag.getFile());
				if (line > -1)
					s.append(":" + line);
			}
			else {
				File f = new File(tag.getFile());
				s.append(f.getName());
				if (line > -1)
					s.append(":" + line);
				s.append("  (" + MiscUtilities.abbreviate(f.getParent()) + ")");
            }
			return s.toString();
		}
	}

	class PreviewTag implements Runnable {
		Vector<Tag> tags;
		public PreviewTag(Vector<Tag> tags) {
			this.tags = tags;
		}
		public void run() {
			tagModel.clear();
			for (int i = 0; i < tags.size(); i++)
				tagModel.addElement(tags.get(i));
			if (! tags.isEmpty())
				Preview.this.tags.setSelectedIndex(0);
		}
	}
	class QueryTag implements Runnable {
		String name;
		public QueryTag(String name) {
			this.name = name;
		}
		public void run() {
			Vector<Tag> tags = CtagsInterfacePlugin.queryScopedTag(Preview.this.view, name);
			if (tags == null)
				return;
			VFSManager.runInAWTThread(new PreviewTag(tags));
		}
	}
	class PreviewBufferLoader implements Runnable {
		Tag tag;
		public PreviewBufferLoader(Tag t) {
			tag = t;
		}
		public void run() {
			file = tag.getFile();
			int line = tag.getLine();
			if (line > -1)
			{
				String s = getContents(file);
				if (s != null)
					VFSManager.runInAWTThread(new PreviewBufferUpdate(s, line));
			}
		}
	}
	class PreviewBufferUpdate implements Runnable {
		String s;
		int line;
		public PreviewBufferUpdate(String s, int line) {
			this.s = s;
			this.line = line;
		}
		public void run() {
			JEditBuffer buffer = text.getBuffer();
			buffer.setReadOnly(false);
			text.setText(s);
			Mode mode = ModeProvider.instance.getModeForFile(file, buffer.getLineText(0));
			if (mode == null)
				mode = ModeProvider.instance.getMode("text");
			buffer.setMode(mode);
			text.scrollTo(line, 0, true);
			text.setCaretPosition(text.getLineStartOffset(line - 1));
			buffer.setReadOnly(true);
		}
	}
	
	private class PreviewTextArea extends JEditEmbeddedTextArea {

		@Override
		public void createPopupMenu(MouseEvent evt) {
			// Create a context menu for the text area
			popup = new JPopupMenu();
			String sel = getSelectedText();
			if (sel != null && sel.length() > 0) {
				JMenuItem copyAction = new JMenuItem(
					jEdit.getProperty(MESSAGE + "copyPreviewSelection"));
				copyAction.addActionListener(new ActionListener() {
					public void actionPerformed(ActionEvent e) {
						copyPreviewSelection();
					}
				});
				popup.add(copyAction);
			}
			JMenuItem jumpAction = new JMenuItem(
				jEdit.getProperty(MESSAGE + "openInEditor"));
			jumpAction.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					CtagsInterfacePlugin.jumpToOffset(Preview.this.view,
						file, getCaretPosition());
				}				
			});
			popup.add(jumpAction);
		}
	}
}
