package ctagsinterface.jedit;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.util.Vector;

import javax.swing.JToolTip;
import javax.swing.Popup;
import javax.swing.PopupFactory;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.ToolTipManager;

import org.gjt.sp.jedit.EditBus;
import org.gjt.sp.jedit.jEdit;
import org.gjt.sp.jedit.EditBus.EBHandler;
import org.gjt.sp.jedit.buffer.JEditBuffer;
import org.gjt.sp.jedit.msg.EditPaneUpdate;
import org.gjt.sp.jedit.msg.PropertiesChanged;
import org.gjt.sp.jedit.textarea.JEditTextArea;
import org.gjt.sp.jedit.textarea.TextAreaExtension;
import org.gjt.sp.jedit.visitors.JEditVisitorAdapter;

import ctagsinterface.main.CtagsInterfacePlugin;
import ctagsinterface.main.Tag;
import ctagsinterface.options.GeneralOptionPane;

public class TagTooltip extends TextAreaExtension
{
	private JEditTextArea textArea;
	private static Attacher attacher;
	
	{
	}
	
	public TagTooltip(JEditTextArea textArea)
	{
		this.textArea = textArea;
	}
	
	public static void start()
	{
		attachToAll();
		attacher = new Attacher();
		EditBus.addToBus(attacher);
	}
	
	private static void attachToAll()
	{
		jEdit.visit(new JEditVisitorAdapter() {
			public void visit(JEditTextArea textArea) {
				attachToTextArea(textArea);
			}
		});
	}

	private static void attachToTextArea(JEditTextArea ta) {
		if (ta.getClientProperty("TagTooltip") != null)
			return;
		TagTooltip ext = new TagTooltip(ta);
		ta.getPainter().addExtension(ext);
		ta.putClientProperty("TagTooltip", ext);
	}
	
	public static void stop()
	{
		EditBus.removeFromBus(attacher);
		attacher = null;
		detachFromAll();
	}
	
	public static void detachFromAll()
	{
		jEdit.visit(new JEditVisitorAdapter() {
			public void visit(JEditTextArea textArea) {
				detachFromTextArea(textArea);
			}
		});
	}

	private static void detachFromTextArea(JEditTextArea ta) {
		TagTooltip ext = (TagTooltip) ta.getClientProperty("TagTooltip");
		if (ext != null)
		{
			ta.getPainter().removeExtension(ext);
			ta.putClientProperty("TagTooltip", null);
		}
	}
	
	@Override
	public String getToolTipText(final int x, final int y) {
		int offset = textArea.xyToOffset(x, y);
		JEditBuffer buffer = textArea.getBuffer();
		int line = buffer.getLineOfOffset(offset);
		int index = offset - buffer.getLineStartOffset(line);
		final String tag = CtagsInterfacePlugin.getTagAt(textArea, line,
			index);
		if ((tag == null) || (tag.length() == 0))
			return null;
		SwingWorker w = new DelayedTooltip(x, y, tag);
		w.execute();
		return null;
	}
	
	private String getTagString(Tag tag)
	{
		StringBuffer sb = new StringBuffer();
		sb.append(tag.getName());
		String signature = tag.getExtension("signature");
		if (signature != null && signature.length() > 0)
			sb.append(signature + " ");
		StringBuffer details = new StringBuffer();
		String namespace = tag.getNamespace();
		if (namespace != null && namespace.length() > 0)
			details.append(namespace);
		String kind = tag.getKind();
		if (kind != null && kind.length() > 0)
		{
			if (details.length() > 0)
				details.append(" ");
			details.append(tag.getKind());
		}
		if (details.length() > 0)
			sb.append(" (" + details.toString() + ")");
		return sb.toString();
	}

	private final class DelayedTooltip extends SwingWorker<String, Void> {
		private int x;
		private int y;
		private String tag;
		private Timer timer = null;
		private Popup popup = null;
		private boolean dismissed = false;
		private ActionListener dismiss;
		private MouseMotionListener mml;
		private KeyListener kl;
		
		private DelayedTooltip(int x, int y, String tag) {
			this.x = x;
			this.y = y;
			this.tag = tag;
			dismiss = new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					dismissed = true;
					if (mml != null)
						textArea.getPainter().removeMouseMotionListener(mml);
					if (kl != null)
						textArea.getPainter().removeKeyListener(kl);
					if (timer != null)
						timer.stop();
					if (popup != null)
						popup.hide();
				}
			};
			mml = new MouseMotionListener() {
				public void mouseDragged(MouseEvent e) { dismiss(); }
				public void mouseMoved(MouseEvent e) { dismiss(); }
			};
			textArea.getPainter().addMouseMotionListener(mml);
			kl = new KeyListener() {
				public void keyPressed(KeyEvent e) { dismiss(); }
				public void keyReleased(KeyEvent e) { dismiss(); }
				public void keyTyped(KeyEvent e) { dismiss(); }
			};
			textArea.getPainter().addKeyListener(kl);
		}

		private void dismiss() {
			dismiss.actionPerformed(null);
		}
		
		@Override
		protected void done() {
			if (dismissed)
				return;
			JToolTip tt = textArea.createToolTip();
			try {
				String ttText = get();
				if (ttText == null)
					return;
				tt.setTipText(ttText);
			} catch (Exception e) {
				return;
			}
			PopupFactory factory = PopupFactory.getSharedInstance();
			int x1 = textArea.getLocationOnScreen().x + x;
			int y1 = textArea.getLocationOnScreen().y + y;
			popup = factory.getPopup(textArea, tt, x1, y1);
			popup.show();
			int d = ToolTipManager.sharedInstance().getDismissDelay();
			timer = new Timer(d, dismiss);
			timer.start();
		}

		@Override
		protected String doInBackground() throws Exception {
			Vector<Tag> tags = CtagsInterfacePlugin.queryScopedTag(
					textArea.getView(), tag);
			if (tags == null || tags.isEmpty())
				return null;
			StringBuffer sb = new StringBuffer("<html>");
			boolean first = true;
			for (Tag t: tags) {
				if (! first)
					sb.append("<br>");
				else
					first = false;
				sb.append(getTagString(t));
			}
			sb.append("</html>");
			return sb.toString();
		}
	}

	public static class Attacher
	{
		@EBHandler
		public void handleEditPaneUpdate(EditPaneUpdate epu)
		{
			if (! GeneralOptionPane.getShowTooltips())
				return;
			if (epu.getWhat().equals(EditPaneUpdate.CREATED))
				attachToTextArea(epu.getEditPane().getTextArea());
			else if (epu.getWhat().equals(EditPaneUpdate.DESTROYED))
				detachFromTextArea(epu.getEditPane().getTextArea());
		}
		@EBHandler
		public void handlePropertiesChanged(PropertiesChanged msg) {
			if (GeneralOptionPane.getShowTooltips())
				attachToAll();
			else
				detachFromAll();
		}
	}
}
