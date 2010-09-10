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

public class ProjectRemoveAction extends Action
{
	public ProjectRemoveAction()
	{
		super("remove-project-tags");
	}
	@Override
	public String getText()
	{
		return "Remove project from tag index";
	}
	public void actionPerformed(ActionEvent arg0)
	{
		if (viewer != null)
		{
			VPTNode sel = viewer.getSelectedNode();
			while (sel != null && (! sel.isProject()))
				sel = (VPTNode) sel.getParent();
			if (sel == null)
				return;
			VPTProject p = (VPTProject) sel;
			String name = p.getName();
			Logger logger = CtagsInterfacePlugin.getLogger(viewer.getView(),
				"Removing project " + name);
			CtagsInterfacePlugin.deleteOrigin(logger, OriginType.PROJECT, name);
		}
	}
}