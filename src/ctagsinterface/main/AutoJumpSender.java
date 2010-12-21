package ctagsinterface.main;

import ise.plugin.nav.AutoJump;

import org.gjt.sp.jedit.EditBus;
import org.gjt.sp.jedit.View;

public class AutoJumpSender
{
	public static void sendAutoJump(final View view, Object what) {
		AutoJump aj = new AutoJump(view, what);
		EditBus.send(aj);
	}

}
