package ctagsinterface.dockables;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import org.gjt.sp.jedit.View;
import org.gjt.sp.util.ThreadUtilities;

import ctagsinterface.main.Logger;
import ctagsinterface.options.GeneralOptionPane;

@SuppressWarnings("serial")
public class Progress extends JPanel
{
	private JTabbedPane tabs;
	private HashMap<Logger, ProgressTab> progressTabs =
		new HashMap<Logger, ProgressTab>();

	public Progress(View view)
	{
		setLayout(new BorderLayout());
		tabs = new JTabbedPane();
		add(tabs, BorderLayout.CENTER);
		tabs.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (SwingUtilities.isMiddleMouseButton(e))
				{
					int i = tabs.getSelectedIndex();
					if (i < 0)
						return;
					closeTab((ProgressTab) tabs.getComponentAt(i));
				}
			}
		});
	}
	public void add(final Logger logger, final String s)
	{
		ThreadUtilities.runInDispatchThread(new Runnable()
		{
			public void run()
			{
				getTab(logger).append(s);
			}
		});
	}
	public void setProgressParams(final Logger logger, final int min, final int max)
	{
		ThreadUtilities.runInDispatchThread(new Runnable()
		{
			public void run()
			{
				getTab(logger).setProgressParams(min, max);
			}
		});
	}
	public void setProgress(final Logger logger, final int value)
	{
		ThreadUtilities.runInDispatchThread(new Runnable()
		{
			public void run()
			{
				getTab(logger).setProgress(value);
			}
		});
	}
	private ProgressTab getTab(Logger logger)
	{
		ProgressTab tab;
		synchronized(progressTabs)
		{
			tab = progressTabs.get(logger);
			if (tab == null)
			{
				tab = new ProgressTab(logger);
				tabs.addTab(logger.name(), tab);
				progressTabs.put(logger, tab);
			}
		}
		return tab;
	}
	public void beginTask(final Logger logger, final String task)
	{
		ThreadUtilities.runInDispatchThread(new Runnable()
		{
			public void run()
			{
				getTab(logger).beginTask(task);
			}
		});

	}
	public void endTask(final Logger logger)
	{
		ThreadUtilities.runInDispatchThread(new Runnable()
		{
			public void run()
			{
				getTab(logger).endTask();
			}
		});
	}
	public void closeTab(ProgressTab tab)
	{
		synchronized(progressTabs)
		{
			progressTabs.remove(tab.logger);
		}
		tabs.remove(tab);
	}
	private class ProgressTab extends JPanel
	{
		private static final String IDLE = "Idle";
		private Logger logger;
		private JTextArea textArea;
		private ArrayList<String> toAdd = new ArrayList<String>();
		private JButton close;
		private JLabel status;
		private Timer timer = null;
		JProgressBar bar;

		public ProgressTab(Logger logger)
		{
			this.logger = logger;
			setLayout(new BorderLayout());
			JPanel top = new JPanel();
			add(top, BorderLayout.NORTH);
			top.setLayout(new BorderLayout(10, 5));
			close = new JButton("Close");
			top.add(close, BorderLayout.EAST);
			close.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent e)
				{
					closeTab(ProgressTab.this);
				}
			});
			status = new JLabel(IDLE);
			top.add(status, BorderLayout.WEST);
			bar = new JProgressBar();
			top.add(bar, BorderLayout.CENTER);
			bar.setVisible(false);
			bar.setStringPainted(true);
			textArea = new JTextArea();
			add(new JScrollPane(textArea), BorderLayout.CENTER);
			timer = new Timer(5000, new ActionListener()
			{
				public void actionPerformed(ActionEvent arg0)
				{
					closeTab(ProgressTab.this);
				}
			});
		}
		public void append(String s)
		{
			boolean update;
			synchronized(toAdd)
			{
				update = toAdd.isEmpty();
				toAdd.add(s);
			}
			if (update)
			{
				SwingUtilities.invokeLater(new Runnable()
				{
					public void run()
					{
						synchronized(toAdd)
						{
							for (String s: toAdd)
								textArea.append(s + "\n");
							toAdd.clear();
						}
						textArea.setCaretPosition(textArea.getText().length());
					}
				});
			}
		}
		public void beginTask(String task)
		{
			timer.stop();
			status.setText(task);
		}
		public void endTask()
		{
			status.setText(IDLE);
			if (GeneralOptionPane.getAutoCloseProgress())
				timer.start();
		}
		public void setProgressParams(int min, int max)
		{
			bar.setVisible(true);
			bar.setMinimum(min);
			bar.setMaximum(max);
		}
		public void setProgress(int value)
		{
			bar.setValue(value);
		}
	}
}
