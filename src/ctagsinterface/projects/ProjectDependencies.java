package ctagsinterface.projects;

import java.awt.GridBagConstraints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;
import java.util.Vector;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import org.gjt.sp.jedit.AbstractOptionPane;
import org.gjt.sp.jedit.GUIUtilities;
import org.gjt.sp.jedit.OptionGroup;
import org.gjt.sp.jedit.OptionPane;
import org.gjt.sp.jedit.jEdit;
import org.gjt.sp.jedit.gui.RolloverButton;

import projectviewer.ProjectManager;
import projectviewer.config.OptionsService;
import projectviewer.vpt.VPTProject;
import ctagsinterface.index.TagIndex;
import ctagsinterface.main.CtagsInterfacePlugin;
import ctagsinterface.index.TagIndex.OriginType;

@SuppressWarnings("serial")
public class ProjectDependencies extends AbstractOptionPane
{
	// TODO: single file dependency
	private static final String PROJECT_DEPENDENCY = "projectDependency";
	private static final String TREE_DEPENDENCY = "treeDependency";
	private static final String TAGFILE_DEPENDENCY = "tagFileDependency";
	JList projects;
	JList trees;
	JList tagFiles;
	DefaultListModel projectsModel;
	DefaultListModel treesModel;
	DefaultListModel tagFilesModel;
	VPTProject project;

	public ProjectDependencies(VPTProject project)
	{
		super("CtagsInterface-ProjectDependencies");
		this.project = project;
	}

	private interface DependencyAsker
	{
		String getDependency();
	}
	protected void _init()
	{
		refreshDependencies();
		projectsModel = getListModel(PROJECT_DEPENDENCY);
		projects = createList("Projects:", projectsModel, new DependencyAsker ()
		{
			public String getDependency()
			{
				return showProjectSelectionDialog();
			}
		});
		addSeparator();
		treesModel = getListModel(TREE_DEPENDENCY);
		trees = createList("Trees:", treesModel, new DependencyAsker () {
			public String getDependency() {
				return showSourceTreeSelectionDialog();
			}
		});
		addSeparator();
		tagFilesModel = getListModel(TAGFILE_DEPENDENCY);
		tagFiles = createList("Tag files:", tagFilesModel, new DependencyAsker () {
			public String getDependency() {
				return showTagFileSelectionDialog();
			}
		});
	}

	private void refreshDependencies()
	{
		refreshDependency(PROJECT_DEPENDENCY, OriginType.PROJECT);
		refreshDependency(TREE_DEPENDENCY, OriginType.DIRECTORY);
		refreshDependency(TAGFILE_DEPENDENCY, OriginType.TAGFILE);
	}

	private void refreshDependency(String dependencyType, OriginType originType)
	{
		Vector <String> origins = new Vector<String>();
		CtagsInterfacePlugin.getIndex().getOrigins(originType, origins);
		Vector <String> dependencies = getListProperty(dependencyType);
		dependencies.retainAll(origins);
		setListProperty(dependencyType, dependencies);
	}

	private void setListModel(String propertyName, DefaultListModel model)
	{
		Vector<String> list = new Vector<String>();
		for (int i = 0; i < model.size(); i++)
			list.add((String) model.getElementAt(i));
		setListProperty(propertyName, list);
	}
	private DefaultListModel getListModel(String propertyName)
	{
		Vector<String> list = getListProperty(propertyName);
		DefaultListModel model = new DefaultListModel();
		for (int i = 0; i < list.size(); i++)
			model.addElement(list.get(i));
		return model;
	}

	private Vector<String> getListProperty(String propertyName)
	{
		return getListProperty(project, propertyName);
	}
	private void setListProperty(String propertyName, Vector<String> list)
	{
		for (int i = 0; i < list.size(); i++)
			project.setProperty(propertyName + i, list.get(i));
		for (int i = list.size(); true; i++)
		{
			String prop = propertyName + i;
			if (project.getProperty(prop) == null)
				break;
			project.removeProperty(prop);
		}
	}

	private JList createList(String title, final DefaultListModel model,
		final DependencyAsker da)
	{
		addComponent(new JLabel(title));
		final JList list = new JList(model);
		addComponent(new JScrollPane(list), GridBagConstraints.HORIZONTAL);
		JPanel buttons = new JPanel();
		JButton add = new RolloverButton(GUIUtilities.loadIcon("Plus.png"));
		add.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				String s = da.getDependency();
				if (s != null && !s.equals("") && !model.contains(s))
				{
					int index = list.getSelectedIndex();
					model.add(index + 1, s);
					list.setSelectedIndex(index + 1);
				}
			}
		});
		JButton remove = new RolloverButton(GUIUtilities.loadIcon("Minus.png"));
		remove.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				int index = list.getSelectedIndex();
				if (index >= 0)
				{
					model.removeElementAt(index);
					if (index < model.size())
						list.setSelectedIndex(index);
					else if (! model.isEmpty())
						list.setSelectedIndex(model.size() - 1);
				}
			}
		});
		buttons.add(add);
		buttons.add(remove);
		addComponent(buttons);
		return list;
	}

	private String showProjectSelectionDialog()
	{
		ProjectWatcher pw = CtagsInterfacePlugin.getProjectWatcher();
		if (pw == null)
		{
			JOptionPane.showMessageDialog(this, jEdit.getProperty(
				"messages.CtagsInterface.noPVSupport"));
			return null;
		}
		String project = pw.getActiveProject(jEdit.getActiveView());
		Vector<String> nameVec = CtagsInterfacePlugin.getIndex().getOrigins(OriginType.PROJECT);
		nameVec.remove(project);
		return getSelectionDialog(nameVec, projectsModel, "Project");
	}

	private String showSourceTreeSelectionDialog()
	{
		Vector<String> dirsVec = CtagsInterfacePlugin.getIndex().getOrigins(OriginType.DIRECTORY);
		return getSelectionDialog(dirsVec, treesModel, "Source tree");
	}

	private String showTagFileSelectionDialog()
	{
		Vector<String> tagFilesVec = CtagsInterfacePlugin.getIndex().getOrigins(OriginType.TAGFILE);
		return getSelectionDialog(tagFilesVec, tagFilesModel, "Tag file");
	}

	private String getSelectionDialog(Vector<String> nameVec, DefaultListModel listModel, String label)
	{
		String selected = "";
		Object [] listItems = listModel.toArray();
		for (int i = 0; i < listItems.length; i++) {
			nameVec.remove(listItems[i].toString());
		}
		if (nameVec.size()>0) {
			String [] names = new String[nameVec.size()];
			nameVec.toArray(names);
			selected = (String) JOptionPane.showInputDialog(this, "Select a "+ label.toLowerCase()+":",
			label+"s", JOptionPane.QUESTION_MESSAGE, null, names, names[0]);
		} else {
			String other = listModel.size()>0 ? "other " : "";
			JOptionPane.showMessageDialog(this, "No " + other + label.toLowerCase() +"s available.");
		}
		return selected;
	}

	protected void _save()
	{
		setListModel(PROJECT_DEPENDENCY, projectsModel);
		setListModel(TREE_DEPENDENCY, treesModel);
		setListModel(TAGFILE_DEPENDENCY, tagFilesModel);
	}

	public static Vector<String> getListProperty(VPTProject project, String propertyName)
	{
		Vector<String> list = new Vector<String>();
		int i = 0;
		while (true)
		{
			String value = project.getProperty(propertyName + i);
			if (value == null)
				break;
			list.add(value);
			i++;
		}
		return list;
	}

	public static HashMap<String, Vector<String>> getDependencies(String projectName)
	{
		HashMap<String, Vector<String>> map = new HashMap<String, Vector<String>>();
		VPTProject project = ProjectManager.getInstance().getProject(projectName);
		if (project == null)
			return map;
		Vector<String> projectDeps = getListProperty(project, PROJECT_DEPENDENCY);
		map.put(TagIndex.OriginType.PROJECT.name, projectDeps);
		Vector<String> treeDeps = getListProperty(project, TREE_DEPENDENCY);
		map.put(TagIndex.OriginType.DIRECTORY.name, treeDeps);
		Vector<String> tagFileDeps = getListProperty(project, TAGFILE_DEPENDENCY);
		map.put(TagIndex.OriginType.TAGFILE.name, tagFileDeps);
		return map;
	}

	public static class ProjectDependencyOptionService
		implements OptionsService
	{
		public OptionGroup getOptionGroup(VPTProject proj)
		{
			return null;
		}

		public OptionPane getOptionPane(VPTProject proj)
		{
			return new ProjectDependencies(proj);
		}
	}
}
