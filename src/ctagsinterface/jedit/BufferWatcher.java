package ctagsinterface.jedit;

import java.util.Vector;

import org.gjt.sp.jedit.Buffer;
import org.gjt.sp.jedit.EditBus;
import org.gjt.sp.jedit.MiscUtilities;
import org.gjt.sp.jedit.EditBus.EBHandler;
import org.gjt.sp.jedit.msg.BufferUpdate;

import ctagsinterface.index.TagIndex;
import ctagsinterface.main.CtagsInterfacePlugin;
import ctagsinterface.options.DirsOptionPane;
import ctagsinterface.options.GeneralOptionPane;

public class BufferWatcher
{

	private TagIndex index;
	
	public BufferWatcher(TagIndex index)
	{
		EditBus.addToBus(this);
		this.index = index;
	}
	
	public void shutdown()
	{
		EditBus.removeFromBus(this);
	}

	@EBHandler
	public void handleBufferUpdate(BufferUpdate bu)
	{
		if ((GeneralOptionPane.getUpdateOnSave() && bu.getWhat() == BufferUpdate.SAVED) ||
			(GeneralOptionPane.getUpdateOnLoad() && bu.getWhat() == BufferUpdate.LOADED))
		{
			Buffer buffer = bu.getBuffer();
			String file = buffer.getPath();
			if (monitored(file))
				update(file);
			// Update file timestamp in any case, since a "jump to tag" operation
			// may be waiting without knowing if this file is monitored.
			Object lock = CtagsInterfacePlugin.getBufferUpdateLock();
			synchronized(lock)
			{
				buffer.setProperty(CtagsInterfacePlugin.TAGS_UPDATED_BUFFER_PROP,
					Long.valueOf(System.currentTimeMillis()));
				lock.notifyAll();
			}
		}
	}

	private void update(String file)
	{
		CtagsInterfacePlugin.tagSourceFile(file);
	}

	private boolean monitored(String file)
	{
		return (isInMonitoredTree(file) || index.hasSourceFile(file));
	}

	private boolean isInMonitoredTree(String file)
	{
		Vector<String> dirs = DirsOptionPane.getDirs();
		file = MiscUtilities.resolveSymlinks(file);
		for (int i = 0; i < dirs.size(); i++)
		{
			if (file.startsWith(dirs.get(i)))
				return true;
		}
		return false;
	}

}
