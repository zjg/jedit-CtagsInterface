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
import org.gjt.sp.jedit.jEdit;
import org.gjt.sp.jedit.Macros;
import org.gjt.sp.jedit.browser.VFSBrowser;
import org.gjt.sp.jedit.browser.VFSFileChooserDialog;
import org.gjt.sp.jedit.gui.RolloverButton;

import ctagsinterface.index.TagIndex.OriginType;
import ctagsinterface.main.CtagsInterfacePlugin;
import ctagsinterface.main.VFSHelper;

@SuppressWarnings("serial")

/**
 * Pane for adding and removing tag files and individual source files
 */
public class FilesOptionPane extends AbstractOptionPane
{

	static public final String OPTION = CtagsInterfacePlugin.OPTION;
	static public final String MESSAGE = CtagsInterfacePlugin.MESSAGE;
	static public final String TagFiles = OPTION + "TagFiles.";
	static public final String SourceFiles = OPTION + "SourceFiles.";
	private JList tagFiles;
	private DefaultListModel tagFilesModel;
	private JList sourceFiles;
	private DefaultListModel sourceFilesModel;

	public FilesOptionPane()
	{
		super("CtagsInterface-Files");
		setBorder(new EmptyBorder(5, 5, 5, 5));
		sourceFilesModel = new DefaultListModel();
		Vector<String> SourceFileList = getSourceFiles();
		for (int i = 0; i < SourceFileList.size(); i++)
			sourceFilesModel.addElement(SourceFileList.get(i));
		sourceFiles = new JList(sourceFilesModel);
		JScrollPane sourceScroller = new JScrollPane(sourceFiles);
		sourceScroller.setBorder(BorderFactory.createTitledBorder(
				jEdit.getProperty(MESSAGE + "sourceFiles")));
		addComponent(sourceScroller, GridBagConstraints.HORIZONTAL);
		JPanel sourceButtons = new JPanel();
		JButton addSourceFile = new RolloverButton(GUIUtilities.loadIcon("Plus.png"));
		sourceButtons.add(addSourceFile);
		JButton removeSourceFile = new RolloverButton(GUIUtilities.loadIcon("Minus.png"));
		sourceButtons.add(removeSourceFile);
		JButton tagSourceFile = new JButton("Tag");
		sourceButtons.add(tagSourceFile);
		addComponent(sourceButtons);
		addSourceFile.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ae) {
				VFSFileChooserDialog chooser = new VFSFileChooserDialog(
					GUIUtilities.getParentDialog(FilesOptionPane.this),
					jEdit.getActiveView(), null,
					VFSBrowser.OPEN_DIALOG, false, false);
				chooser.setTitle("Select tag file");
				chooser.setVisible(true);
				if (chooser.getSelectedFiles() == null)
					return;
				String SourceFilePath = chooser.getSelectedFiles()[0];
				if (!sourceFilesModel.contains(SourceFilePath))
					sourceFilesModel.addElement(SourceFilePath);
			}
		});

		removeSourceFile.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ae) {
				int i = sourceFiles.getSelectedIndex();
				if (i >= 0)
					sourceFilesModel.removeElementAt(i);
			}
		});

		tagSourceFile.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ae) {
				int i = sourceFiles.getSelectedIndex();
				if (i >= 0) {
					String sourceFilePath = (String) sourceFilesModel.getElementAt(i);
					CtagsInterfacePlugin.addFile(jEdit.getActiveView(), sourceFilePath);
				}
			}
		});

		setBorder(new EmptyBorder(5, 5, 5, 5));
		tagFilesModel = new DefaultListModel();
		Vector<String> tagFileList = getTagFiles();
		for (int i = 0; i < tagFileList.size(); i++)
			tagFilesModel.addElement(tagFileList.get(i));
		tagFiles = new JList(tagFilesModel);
		JScrollPane tagFileScroller = new JScrollPane(tagFiles);
		tagFileScroller.setBorder(BorderFactory.createTitledBorder(
				jEdit.getProperty(MESSAGE + "tagFiles")));
		addComponent(tagFileScroller, GridBagConstraints.HORIZONTAL);
		JPanel tagFileButtons = new JPanel();
		JButton addTagFile = new RolloverButton(GUIUtilities.loadIcon("Plus.png"));
		tagFileButtons.add(addTagFile);
		JButton removeTagFile = new RolloverButton(GUIUtilities.loadIcon("Minus.png"));
		tagFileButtons.add(removeTagFile);
		JButton tagTagFile = new JButton("Tag");
		tagFileButtons.add(tagTagFile);
		addComponent(tagFileButtons);
		addTagFile.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ae) {
				VFSFileChooserDialog chooser = new VFSFileChooserDialog(
					GUIUtilities.getParentDialog(FilesOptionPane.this),
					jEdit.getActiveView(), null,
					VFSBrowser.OPEN_DIALOG, false, false);
				chooser.setTitle("Select tag file");
				chooser.setVisible(true);
				if (chooser.getSelectedFiles() == null)
					return;
				String tagFilePath = chooser.getSelectedFiles()[0];
				if (!VFSHelper.checkFileExtension(tagFilePath, "tag")) {
					Macros.message(jEdit.getActiveView(), "File name needs to end with \".tag\"");
					return;
				}
				if (!tagFilesModel.contains(tagFilePath))
					tagFilesModel.addElement(tagFilePath);
			}
		});

		removeTagFile.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ae) {
				int i = tagFiles.getSelectedIndex();
				if (i >= 0)
					tagFilesModel.removeElementAt(i);
			}
		});

		tagTagFile.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ae) {
				int i = tagFiles.getSelectedIndex();
				if (i >= 0) {
					String tagFile = (String) tagFilesModel.getElementAt(i);
					CtagsInterfacePlugin.addTagFile(tagFile);
				}
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

	public void saveSourceFiles(DefaultListModel model)
	{
		Vector<String> sourceFiles = getSourceFiles();
		// Remove obsolete files
		for (String sf : sourceFiles) {
			if (!sourceFilesModel.contains(sf)) {
				CtagsInterfacePlugin.deleteFile(jEdit.getActiveView(), sf);
			}
		}
		// Add new files
		for (int i = 0; i < sourceFilesModel.size(); i++) {
			String sf = sourceFilesModel.getElementAt(i).toString();
			if (!sourceFiles.contains(sf)) {
				CtagsInterfacePlugin.addFile(jEdit.getActiveView(), sf);
			}
		}
	}

	@Override
	protected void _save()
	{
		saveOrigins(OriginType.TAGFILE, tagFilesModel);
		saveSourceFiles(sourceFilesModel);
	}

	static public Vector<String> getTagFiles()
	{
		return CtagsInterfacePlugin.getIndex().getOrigins(OriginType.TAGFILE);
	}

	static public Vector<String> getSourceFiles()
	{
		return CtagsInterfacePlugin.getIndex().getFilesOfOrigin(OriginType.MISC);
	}
}
