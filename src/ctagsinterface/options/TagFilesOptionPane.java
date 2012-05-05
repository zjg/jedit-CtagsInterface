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
import org.gjt.sp.jedit.Macros;
import org.gjt.sp.jedit.browser.VFSBrowser;
import org.gjt.sp.jedit.browser.VFSFileChooserDialog;
import org.gjt.sp.jedit.gui.RolloverButton;

import ctagsinterface.index.TagIndex.OriginType;
import ctagsinterface.main.CtagsInterfacePlugin;
import ctagsinterface.main.VFSHelper;

@SuppressWarnings("serial")
public class TagFilesOptionPane extends AbstractOptionPane
{

	static public final String OPTION = CtagsInterfacePlugin.OPTION;
	static public final String MESSAGE = CtagsInterfacePlugin.MESSAGE;
	static public final String TagFiles = OPTION + "TagFiles.";
	private JList tagFiles;
	private DefaultListModel tagFilesModel;

	public TagFilesOptionPane()
	{
		super("CtagsInterface-TagFiles");
		setBorder(new EmptyBorder(5, 5, 5, 5));
		tagFilesModel = new DefaultListModel();
		Vector<String> tagFileList = getTagFiles();
		for (int i = 0; i < tagFileList.size(); i++)
			tagFilesModel.addElement(tagFileList.get(i));
		tagFiles = new JList(tagFilesModel);
		JScrollPane scroller = new JScrollPane(tagFiles);
		scroller.setBorder(BorderFactory.createTitledBorder(
				jEdit.getProperty(MESSAGE + "tagFiles")));
		addComponent(scroller, GridBagConstraints.HORIZONTAL);
		JPanel buttons = new JPanel();
		JButton add = new RolloverButton(GUIUtilities.loadIcon("Plus.png"));
		buttons.add(add);
		JButton remove = new RolloverButton(GUIUtilities.loadIcon("Minus.png"));
		buttons.add(remove);
		JButton tag = new JButton("Tag");
		buttons.add(tag);
		addComponent(buttons);
		add.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ae) {
				VFSFileChooserDialog chooser = new VFSFileChooserDialog(
					GUIUtilities.getParentDialog(TagFilesOptionPane.this),
					jEdit.getActiveView(), System.getProperty("user.home"),
					VFSBrowser.OPEN_DIALOG, false, false);
				chooser.setTitle("Select tag file");
				chooser.setVisible(true);
				if (chooser.getSelectedFiles() == null)
					return;
				String tagFilePath = chooser.getSelectedFiles()[0];
				if (!VFSHelper.checkTagFileVFS(tagFilePath)) {
					Macros.message(jEdit.getActiveView(), "File name needs to end with \".tag\"");
					return;
				}
				if (!tagFilesModel.contains(tagFilePath))
					tagFilesModel.addElement(tagFilePath);
			}
		});

		remove.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ae) {
				int i = tagFiles.getSelectedIndex();
				if (i >= 0)
					tagFilesModel.removeElementAt(i);
			}
		});

		tag.addActionListener(new ActionListener() {
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

	@Override
	protected void _save()
	{
		saveOrigins(OriginType.TAGFILE, tagFilesModel);
	}

	static public Vector<String> getTagFiles()
	{
		Vector<String> tagFiles = new Vector<String>();
		CtagsInterfacePlugin.getIndex().getOrigins(OriginType.TAGFILE, tagFiles);
		return tagFiles;
	}
}
