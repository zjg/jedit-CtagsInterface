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

public class ProjectAddAction extends Action
{
	private String name;

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
			Logger logger = CtagsInterfacePlugin.getLogger(viewer.getView(),
				"project " + name);
			CtagsInterfacePlugin.insertOrigin(logger, OriginType.PROJECT, name);
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
		cmItem.setVisible(true);
		if (CtagsInterfacePlugin.getIndex().hasOrigin(origin))
			cmItem.setVisible(false);
	}
}