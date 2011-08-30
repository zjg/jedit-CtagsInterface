package ctagsinterface.options;

import java.awt.GridBagConstraints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.border.EmptyBorder;

import org.gjt.sp.jedit.AbstractOptionPane;
import org.gjt.sp.jedit.GUIUtilities;
import org.gjt.sp.jedit.MiscUtilities;
import org.gjt.sp.jedit.jEdit;
import org.gjt.sp.jedit.browser.VFSBrowser;
import org.gjt.sp.jedit.browser.VFSFileChooserDialog;
import org.gjt.sp.jedit.gui.RolloverButton;

import ctagsinterface.index.TagIndex.OriginType;
import ctagsinterface.main.CtagsInterfacePlugin;
import ctagsinterface.main.VFSHelper;

@SuppressWarnings("serial")
public class DirsOptionPane extends AbstractOptionPane
{

	static public final String OPTION = CtagsInterfacePlugin.OPTION;
	static public final String MESSAGE = CtagsInterfacePlugin.MESSAGE;
	static public final String DIRS = OPTION + "dirs.";
	JList dirs;
	DefaultListModel dirsModel;
	private DefaultListModel archivesModel;
	private JList archives;
	
	public DirsOptionPane()
	{
		super("CtagsInterface-Dirs");
		setBorder(new EmptyBorder(5, 5, 5, 5));

		dirsModel = new DefaultListModel();
		Vector<String> trees = getDirs();
		for (int i = 0; i < trees.size(); i++)
			dirsModel.addElement(trees.get(i));
		dirs = new JList(dirsModel);
		JScrollPane scroller = new JScrollPane(dirs);
		scroller.setBorder(BorderFactory.createTitledBorder(
				jEdit.getProperty(MESSAGE + "dirs")));
		addComponent(scroller, GridBagConstraints.HORIZONTAL);
		JPanel buttons = new JPanel();
		JButton add = new RolloverButton(GUIUtilities.loadIcon("Plus.png"));
		buttons.add(add);
		JButton remove = new RolloverButton(GUIUtilities.loadIcon("Minus.png"));
		buttons.add(remove);
		JButton tag = new JButton("Tag");
		buttons.add(tag);
		addComponent(buttons);

		archivesModel = new DefaultListModel();
		Vector<String> archiveFiles = getArchives();
		for (int i = 0; i < archiveFiles.size(); i++)
			archivesModel.addElement(archiveFiles.get(i));
		archives = new JList(archivesModel);
		scroller = new JScrollPane(archives);
		scroller.setBorder(BorderFactory.createTitledBorder(
				jEdit.getProperty(MESSAGE + "archives")));
		addComponent(scroller, GridBagConstraints.HORIZONTAL);
		buttons = new JPanel();
		JButton addArchive = new RolloverButton(GUIUtilities.loadIcon("Plus.png"));
		buttons.add(addArchive);
		JButton removeArchive = new RolloverButton(GUIUtilities.loadIcon("Minus.png"));
		buttons.add(removeArchive);
		JButton tagArchive = new JButton("Tag");
		buttons.add(tagArchive);
		addComponent(buttons);
		
		add.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ae) {
				VFSFileChooserDialog chooser = new VFSFileChooserDialog(
					GUIUtilities.getParentDialog(DirsOptionPane.this),
					jEdit.getActiveView(), System.getProperty("user.home"),
					VFSBrowser.CHOOSE_DIRECTORY_DIALOG, false, false);
				chooser.setTitle("Select root of source tree");
				chooser.setVisible(true);
				if (chooser.getSelectedFiles() == null)
					return;
				String dir = chooser.getSelectedFiles()[0];
				dirsModel.addElement(MiscUtilities.resolveSymlinks(dir));
			}
		});
		remove.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ae) {
				int i = dirs.getSelectedIndex();
				if (i >= 0)
					dirsModel.removeElementAt(i);
			}
		});
		tag.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ae) {
				int i = dirs.getSelectedIndex();
				if (i >= 0) {
					String tree = (String) dirsModel.getElementAt(i);
					CtagsInterfacePlugin.refreshOrigin(OriginType.DIRECTORY, tree);
				}
			}
		});

		addArchive.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ae) {
				VFSFileChooserDialog chooser = new VFSFileChooserDialog(
					GUIUtilities.getParentDialog(DirsOptionPane.this),
					jEdit.getActiveView(), System.getProperty("user.home"),
					VFSBrowser.OPEN_DIALOG, false, false);
				chooser.setTitle("Select source archive");
				chooser.setVisible(true);
				if (chooser.getSelectedFiles() == null)
					return;
				String archive = chooser.getSelectedFiles()[0];
				if (! VFSHelper.checkArchiveVFS(archive))
					return;
				archivesModel.addElement(archive);
			}
		});
		removeArchive.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ae) {
				int i = archives.getSelectedIndex();
				if (i >= 0)
					archivesModel.removeElementAt(i);
			}
		});
	}

	public void saveOrigins(OriginType origin, DefaultListModel model)
	{
		Vector<String> names = new Vector<String>();
		int nItems = model.size();
		for (int i = 0; i < nItems; i++)
			names.add((String) model.getElementAt(i));
		CtagsInterfacePlugin.updateOrigins(origin, names);
	}

	@Override
	protected void _save()
	{
		saveOrigins(OriginType.DIRECTORY, dirsModel);
		saveOrigins(OriginType.ARCHIVE, archivesModel);
	}
	
	static public Vector<String> getDirs()
	{
		Vector<String> dirs = new Vector<String>();
		CtagsInterfacePlugin.getIndex().getOrigins(OriginType.DIRECTORY, dirs);
		return dirs;
	}
	
	static public Vector<String> getArchives()
	{
		Vector<String> archives = new Vector<String>();
		CtagsInterfacePlugin.getIndex().getOrigins(OriginType.ARCHIVE, archives);
		return archives;
	}
}
