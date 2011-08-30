package ctagsinterface.options;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

import javax.swing.InputVerifier;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;

import org.gjt.sp.jedit.AbstractOptionPane;
import org.gjt.sp.jedit.EditBus;
import org.gjt.sp.jedit.jEdit;
import org.gjt.sp.jedit.msg.PropertiesChanged;

import ctagsinterface.main.CtagsInterfacePlugin;

@SuppressWarnings("serial")
public class GeneralOptionPane extends AbstractOptionPane {

	static public final String OPTION = CtagsInterfacePlugin.OPTION;
	static public final String MESSAGE = CtagsInterfacePlugin.MESSAGE;
	static public final String CTAGS = OPTION + "ctags";
	static public final String CMD = OPTION + "cmd";
	static public final String PATTERN = OPTION + "pattern";
	static public final String UPDATE_ON_LOAD = OPTION + "updateOnLoad";
	static public final String UPDATE_ON_SAVE = OPTION + "updateOnSave";
	static public final String TOOLTIPS = OPTION + "tooltips";
	static public final String COMPLETE_DESC = OPTION + "completeDesc";
	static public final String PREVIEW_VERTICAL_SPLIT = OPTION + "previewVerticalSplit";
	static public final String PREVIEW_TOOLBAR = OPTION + "previewToolbar";
	static public final String PREVIEW_WRAP = OPTION + "previewWrap";
	static public final String PREVIEW_DELAY = OPTION + "previewDelay";
	static public final String AUTO_CLOSE_PROGRESS = OPTION + "autoCloseProgress";
	static private final String CHECK_CTAGS = MESSAGE + "checkCtags";
	static private final String BAD_CTAGS_PATH = MESSAGE + "badCtagsPath";
	static private final String GOOD_CTAGS_PATH= MESSAGE + "goodCtagsPath";
	JTextField ctags;
	JButton checkCtags;
	JTextField cmd;
	JTextField pattern;
	JCheckBox updateOnLoad;
	JCheckBox updateOnSave;
	JCheckBox background;
	JCheckBox tooltips;
	JCheckBox completeDesc;
	JCheckBox previewVerticalSplitter;
	JCheckBox previewToolbar;
	JCheckBox previewWrap;
	JTextField previewDelay;
	JCheckBox autoCloseProgress;

	public GeneralOptionPane() {
		super("CtagsInterface-General");
		setBorder(new EmptyBorder(5, 5, 5, 5));

		JPanel ctagsPanel = new JPanel();
		ctags = new JTextField(jEdit.getProperty(CTAGS), 40);
		ctagsPanel.add(ctags);
		checkCtags = new JButton(jEdit.getProperty(CHECK_CTAGS));
		ctagsPanel.add(checkCtags);
		checkCtags.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e)
			{
				checkInstall();
			}
		});
		addComponent(jEdit.getProperty(MESSAGE + "ctags"), ctagsPanel);

		cmd = new JTextField(jEdit.getProperty(CMD), 40);
		addComponent(jEdit.getProperty(MESSAGE + "cmd"), cmd);
		
		pattern = new JTextField(jEdit.getProperty(PATTERN), 40);
		addComponent(jEdit.getProperty(MESSAGE + "pattern"), pattern);
		
		updateOnLoad = new JCheckBox(jEdit.getProperty(MESSAGE + "updateOnLoad"),
			getUpdateOnLoad());
		addComponent(updateOnLoad);
		updateOnSave = new JCheckBox(jEdit.getProperty(MESSAGE + "updateOnSave"),
			getUpdateOnSave());
		addComponent(updateOnSave);
		
		tooltips = new JCheckBox(jEdit.getProperty(MESSAGE + "showTooltips"),
			getShowTooltips());
		addComponent(tooltips);
		
		completeDesc = new JCheckBox(jEdit.getProperty(MESSAGE + "completeDesc"),
			getCompleteDesc());
		addComponent(completeDesc);
		
		JPanel previewPanel = new JPanel();
		previewPanel.setLayout(new GridLayout(0, 1));
		previewPanel.setBorder(new TitledBorder(jEdit.getProperty(
			MESSAGE + "previewTitle")));
		previewVerticalSplitter = new JCheckBox(jEdit.getProperty(MESSAGE + "previewVerticalSplit"),
				getPreviewVerticalSplit());
		previewPanel.add(previewVerticalSplitter);
		previewToolbar = new JCheckBox(jEdit.getProperty(MESSAGE + "previewToolbar"),
				getPreviewToolbar());
		previewPanel.add(previewToolbar);
		previewWrap = new JCheckBox(jEdit.getProperty(MESSAGE + "previewWrap"),
				jEdit.getBooleanProperty(PREVIEW_WRAP));
		previewPanel.add(previewWrap);
		JPanel previewDelayPanel = new JPanel(new BorderLayout());
		previewDelayPanel.add(new JLabel(jEdit.getProperty(MESSAGE + "previewDelay")),
			BorderLayout.WEST);
		previewDelay = new JTextField(String.valueOf(
			jEdit.getIntegerProperty(PREVIEW_DELAY)), 5);
		previewDelay.setInputVerifier(new InputVerifier() {
			public boolean verify(JComponent c) {
				try {
					Integer.valueOf(previewDelay.getText());
				} catch (Exception e) {
					return false;
				}
				return true;
			}
		});
		previewDelayPanel.add(previewDelay, BorderLayout.EAST);
		previewPanel.add(previewDelayPanel);
		addComponent(previewPanel);

		autoCloseProgress = new JCheckBox(jEdit.getProperty(MESSAGE + "autoCloseProgress"),
			getAutoCloseProgress());
		addComponent(autoCloseProgress);
	}

	@Override
	protected void _save() {
		jEdit.setProperty(CTAGS, ctags.getText());
		jEdit.setProperty(CMD, cmd.getText());
		jEdit.setProperty(PATTERN, pattern.getText());
		jEdit.setBooleanProperty(UPDATE_ON_LOAD, updateOnLoad.isSelected());
		jEdit.setBooleanProperty(UPDATE_ON_SAVE, updateOnSave.isSelected());
		jEdit.setBooleanProperty(TOOLTIPS, tooltips.isSelected());
		jEdit.setBooleanProperty(COMPLETE_DESC, completeDesc.isSelected());
		jEdit.setBooleanProperty(PREVIEW_VERTICAL_SPLIT, previewVerticalSplitter.isSelected());
		jEdit.setBooleanProperty(PREVIEW_TOOLBAR, previewToolbar.isSelected());
		jEdit.setBooleanProperty(PREVIEW_WRAP, previewWrap.isSelected());
		jEdit.setIntegerProperty(PREVIEW_DELAY, Integer.valueOf(previewDelay.getText()));
		jEdit.setBooleanProperty(AUTO_CLOSE_PROGRESS, autoCloseProgress.isSelected());
		EditBus.send(new PropertiesChanged(null));
	}

	private boolean checkInstall() {
		String path = ctags.getText();
		File f = new File(path);
		if ((! f.exists()) || (! f.canExecute())) {
			JOptionPane.showMessageDialog(this, jEdit.getProperty(BAD_CTAGS_PATH));
			return false;
		}
		JOptionPane.showMessageDialog(this, jEdit.getProperty(GOOD_CTAGS_PATH));
		return true;
	}

	public static String getCtags() {
		String s = jEdit.getProperty(CTAGS);
		if (s == null || s.length() == 0)
			return "ctags";
		return s;
	}
	public static String getCmd() {
		String s = jEdit.getProperty(CMD);
		if (s == null)
			return "";
		return s;
	}
	public static String getPattern() {
		String s = jEdit.getProperty(PATTERN);
		if (s == null)
			return "";
		return s;
	}
	public static boolean getUpdateOnSave() {
		return jEdit.getBooleanProperty(UPDATE_ON_SAVE, true);
	}
	public static boolean getUpdateOnLoad() {
		return jEdit.getBooleanProperty(UPDATE_ON_LOAD, true);
	}
	public static boolean getShowTooltips() {
		return jEdit.getBooleanProperty(TOOLTIPS, true);
	}
	public static boolean getCompleteDesc() {
		return jEdit.getBooleanProperty(COMPLETE_DESC, true);
	}
	public static boolean getPreviewVerticalSplit() {
		return jEdit.getBooleanProperty(PREVIEW_VERTICAL_SPLIT, true);
	}
	public static boolean getPreviewToolbar() {
		return jEdit.getBooleanProperty(PREVIEW_TOOLBAR, true);
	}
	public static boolean getPreviewWrap() {
		return jEdit.getBooleanProperty(PREVIEW_WRAP, true);
	}
	public static void setPreviewWrap(boolean wrap) {
		jEdit.setBooleanProperty(PREVIEW_WRAP, wrap);
	}
	public static int getPreviewDelay() {
		return jEdit.getIntegerProperty(PREVIEW_DELAY, 0);
	}
	public static boolean getAutoCloseProgress() {
		return jEdit.getBooleanProperty(AUTO_CLOSE_PROGRESS, true);
	}
}
