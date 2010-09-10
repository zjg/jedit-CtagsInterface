/**
 * 
 */
package ctagsinterface.projects;

import java.awt.event.ActionEvent;

import projectviewer.action.Action;
import projectviewer.vpt.VPTNode;
import projectviewer.vpt.VPTProject;
import ctagsinterface.index.TagIndex.OriginType;
import ctagsinterface.main.CtagsInterfacePlugin;
import ctagsinterface.main.Logger;

public class ProjectAddAction extends Action
{
	public ProjectAddAction()
	{
		super("add-project-tags");
	}
	@Override
	public String getText()
	{
		return "Add project to tag index";
	}
	public void actionPerformed(ActionEvent arg0)
	{
		if (viewer != null)
		{
			VPTNode node = viewer.getSelectedNode();
			if (node == null)
				node = viewer.getRoot();
			VPTProject project = VPTNode.findProjectFor(node);
			if (project == null)
				return;
			String name = project.getName();
			Logger logger = CtagsInterfacePlugin.getLogger(viewer.getView(),
				"project " + name);
			CtagsInterfacePlugin.insertOrigin(logger, OriginType.PROJECT, name);
		}
	}
}