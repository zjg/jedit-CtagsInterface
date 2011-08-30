package ctagsinterface.options;

import java.awt.GridBagConstraints;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;

import org.gjt.sp.jedit.AbstractOptionPane;
import org.gjt.sp.jedit.GUIUtilities;
import org.gjt.sp.jedit.jEdit;
import org.gjt.sp.jedit.gui.RolloverButton;
import org.gjt.sp.util.StandardUtilities;

import ctagsinterface.index.TagIndex.OriginType;
import ctagsinterface.main.CtagsInterfacePlugin;
import ctagsinterface.projects.ProjectWatcher;

@SuppressWarnings("serial")
public class ProjectsOptionPane extends AbstractOptionPane
{
	static public final String OPTION = CtagsInterfacePlugin.OPTION;
	static public final String MESSAGE = CtagsInterfacePlugin.MESSAGE;
	static public final String PROJECTS = OPTION + "projects.";
	static public final String TRACK_PROJECTS = OPTION + "trackProjectList";
	static public final String AUTO_UPDATE = OPTION + "autoUpdateProjects";
	static public final String GLOBAL = OPTION + "searchGlobally";
	static public final String ACTIVE_ONLY = OPTION + "searchActiveProjectOnly";
	static public final String ACTIVE_AND_DEPS = OPTION + "searchActiveProjectAndDeps";
	static public final String ACTIVE_FIRST = OPTION + "searchActiveProjectFirst";
	JList projects;
	DefaultListModel projectsModel;
	ProjectWatcher pvi;
	JCheckBox trackProjectList;
	JCheckBox autoUpdate;
	JRadioButton global;
	JRadioButton activeOnly;
	JRadioButton activeAndDeps;
	JRadioButton activeFirst;
	
	public ProjectsOptionPane()
	{
		super("CtagsInterface-Projects");
		setBorder(new EmptyBorder(5, 5, 5, 5));

		projectsModel = new DefaultListModel();
		Vector<String> trees = getProjects();
		for (int i = 0; i < trees.size(); i++)
			projectsModel.addElement(trees.get(i));
		projects = new JList(projectsModel);
		JScrollPane scroller = new JScrollPane(projects);
		scroller.setBorder(BorderFactory.createTitledBorder(
				jEdit.getProperty(MESSAGE + "projects")));
		addComponent(scroller, GridBagConstraints.HORIZONTAL);
		JPanel buttons = new JPanel();
		JButton add = new RolloverButton(GUIUtilities.loadIcon("Plus.png"));
		buttons.add(add);
		JButton remove = new RolloverButton(GUIUtilities.loadIcon("Minus.png"));
		buttons.add(remove);
		JButton tag = new JButton("Tag");
		buttons.add(tag);
		addComponent(buttons);
		trackProjectList = new JCheckBox(jEdit.getProperty(MESSAGE + "trackProjectList"),
			jEdit.getBooleanProperty(TRACK_PROJECTS));
		addComponent(trackProjectList);
		autoUpdate = new JCheckBox(jEdit.getProperty(MESSAGE + "autoUpdateProjects"),
			jEdit.getBooleanProperty(AUTO_UPDATE));
		addComponent(autoUpdate);
		JPanel p = new JPanel(new GridLayout(0, 1));
		Border b = BorderFactory.createTitledBorder(jEdit.getProperty(MESSAGE + "searchScope"));
		p.setBorder(b);
		addComponent(p);
		ButtonGroup g = new ButtonGroup();
		global = new JRadioButton(jEdit.getProperty(MESSAGE + "searchGlobally"),
			jEdit.getBooleanProperty(GLOBAL));
		p.add(global);
		g.add(global);
		activeOnly = new JRadioButton(jEdit.getProperty(MESSAGE + "searchActiveProjectOnly"),
			jEdit.getBooleanProperty(ACTIVE_ONLY));
		p.add(activeOnly);
		g.add(activeOnly);
		activeAndDeps = new JRadioButton(jEdit.getProperty(MESSAGE + "searchActiveProjectAndDeps"),
				jEdit.getBooleanProperty(ACTIVE_AND_DEPS));
		p.add(activeAndDeps);
		g.add(activeAndDeps);
		activeFirst = new JRadioButton(jEdit.getProperty(MESSAGE + "searchActiveProjectFirst"),
			jEdit.getBooleanProperty(ACTIVE_FIRST));
		p.add(activeFirst);
		g.add(activeFirst);

		pvi = CtagsInterfacePlugin.getProjectWatcher();
		if (pvi == null)
		{
			add.setEnabled(false);
			tag.setEnabled(false);
		}
		else
		{
			add.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent ae)
				{
					Vector<String> nameVec = pvi.getProjects();
					String [] names = new String[nameVec.size()];
					nameVec.toArray(names);
					Arrays.sort(names, new StandardUtilities.StringCompare<String>(true));
					String selected = (String) JOptionPane.showInputDialog(
						ProjectsOptionPane.this, "Select project:", "Projects",
						JOptionPane.QUESTION_MESSAGE, null, names, names[0]);
					if (selected != null)
						projectsModel.addElement(selected);
				}
			});
			tag.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent ae)
				{
					int i = projects.getSelectedIndex();
					if (i >= 0)
					{
						String project = (String) projectsModel.getElementAt(i);
						CtagsInterfacePlugin.refreshOrigin(OriginType.PROJECT,
							project);
					}
				}
			});
		}
		remove.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent ae)
			{
				int i = projects.getSelectedIndex();
				if (i >= 0)
					projectsModel.removeElementAt(i);
			}
		});
	}

	@Override
	protected void _save()
	{
		Vector<String> names = new Vector<String>();
		int nProjects = projectsModel.size(); 
		for (int i = 0; i < nProjects; i++)
			names.add((String) projectsModel.getElementAt(i));
		CtagsInterfacePlugin.updateOrigins(OriginType.PROJECT, names);
		jEdit.setBooleanProperty(TRACK_PROJECTS, trackProjectList.isSelected());
		jEdit.setBooleanProperty(AUTO_UPDATE, autoUpdate.isSelected());
		jEdit.setBooleanProperty(GLOBAL, global.isSelected());
		jEdit.setBooleanProperty(ACTIVE_ONLY, activeOnly.isSelected());
		jEdit.setBooleanProperty(ACTIVE_AND_DEPS, activeAndDeps.isSelected());
		jEdit.setBooleanProperty(ACTIVE_FIRST, activeFirst.isSelected());
	}
	
	static public Vector<String> getProjects()
	{
		Vector<String> projects = new Vector<String>();
		CtagsInterfacePlugin.getIndex().getOrigins(OriginType.PROJECT, projects);
		return projects;
	}
	static public boolean getTrackProjectList()
	{
		return jEdit.getBooleanProperty(TRACK_PROJECTS);
	}
	static public boolean getAutoUpdateProjects()
	{
		return jEdit.getBooleanProperty(AUTO_UPDATE);
	}
	static public boolean getSearchGlobally()
	{
		return jEdit.getBooleanProperty(GLOBAL);
	}
	static public boolean getSearchActiveProjectOnly()
	{
		return jEdit.getBooleanProperty(ACTIVE_ONLY);
	}
	static public boolean getSearchActiveProjectAndDeps()
	{
		return jEdit.getBooleanProperty(ACTIVE_AND_DEPS);
	}
	static public boolean getSearchActiveProjectFirst()
	{
		return jEdit.getBooleanProperty(ACTIVE_FIRST);
	}
}
