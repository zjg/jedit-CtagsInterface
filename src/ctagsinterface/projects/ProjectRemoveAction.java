/**
 *
 */
package ctagsinterface.projects;

import java.awt.event.ActionEvent;

import projectviewer.action.Action;
import projectviewer.vpt.VPTNode;
import projectviewer.vpt.VPTProject;
import ctagsinterface.index.TagIndex.Origin;
import ctagsinterface.index.TagIndex.OriginType;
import ctagsinterface.main.CtagsInterfacePlugin;
import ctagsinterface.main.Logger;

public class ProjectRemoveAction extends Action
{
	String name;
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
		{
			Logger logger = CtagsInterfacePlugin.getLogger(viewer.getView(),
				"Removing project " + name);
			CtagsInterfacePlugin.deleteOrigin(logger, OriginType.PROJECT, name);
		}
	}
	@Override
	public void prepareForNode(VPTNode node) {

		if (node == null) {
			cmItem.setVisible(false);
			return;
		}
		while (node != null && (! node.isProject()))
			node = (VPTNode) node.getParent();
		if (node == null)
			return;
		VPTProject p = (VPTProject) node;
		name = p.getName();
		Origin origin = CtagsInterfacePlugin.getIndex().getOrigin(OriginType.PROJECT, name, false);
		cmItem.setVisible(false);
		if (CtagsInterfacePlugin.getIndex().hasOrigin(origin))
			cmItem.setVisible(true);
	}

}