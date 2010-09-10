package ctagsinterface.options;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;

import org.gjt.sp.jedit.AbstractOptionPane;
import org.gjt.sp.jedit.EditAction;
import org.gjt.sp.jedit.GUIUtilities;
import org.gjt.sp.jedit.jEdit;
import org.gjt.sp.jedit.gui.RolloverButton;

import ctagsinterface.main.CtagsInterfacePlugin;
import ctagsinterface.main.QueryAction;
import ctagsinterface.main.QueryAction.QueryType;

@SuppressWarnings("serial")
public class ActionsOptionPane extends AbstractOptionPane
{
	static public final String OPTION = CtagsInterfacePlugin.OPTION;
	static public final String MESSAGE = CtagsInterfacePlugin.MESSAGE;
	static public final String ACTIONS = OPTION + "actions.";
	private JList actions;
	private DefaultListModel actionsModel;

	public ActionsOptionPane()
	{
		super("CtagsInterface-Actions");
		setBorder(new EmptyBorder(5, 5, 5, 5));

		actionsModel = new DefaultListModel();
		QueryAction[] queries = loadActions();
		for (QueryAction querie : queries)
			actionsModel.addElement(querie);
		actions = new JList(actionsModel);
		JScrollPane scroller = new JScrollPane(actions);
		scroller.setBorder(BorderFactory.createTitledBorder(
				jEdit.getProperty(MESSAGE + "actions")));
		addComponent(scroller, GridBagConstraints.HORIZONTAL);
		JPanel buttons = new JPanel();
		JButton add = new RolloverButton(GUIUtilities.loadIcon("Plus.png"));
		buttons.add(add);
		JButton remove = new RolloverButton(GUIUtilities.loadIcon("Minus.png"));
		buttons.add(remove);
		JButton edit = new JButton("Edit");
		buttons.add(edit);
		addComponent(buttons);

		add.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent ae)
			{
				EditAction action = new ActionEditor().getAction();
				if (action != null)
					actionsModel.addElement(action);
			}
		});
		remove.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent ae)
			{
				int i = actions.getSelectedIndex();
				if (i >= 0)
					actionsModel.removeElementAt(i);
			}
		});
		edit.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent ae)
			{
				int i = actions.getSelectedIndex();
				if (i < 0)
					return;
				QueryAction action = (QueryAction) actionsModel.getElementAt(i);
				action = new ActionEditor(action).getAction();
				if (action != null)
					actionsModel.setElementAt(action, i);
			}
		});
	}

	static public QueryAction[] loadActions()
	{
		int n = jEdit.getIntegerProperty(ACTIONS + "size", 0);
		QueryAction[] actionArr = new QueryAction[n];
		for (int i = 0; i < n; i++)
			actionArr[i] = new QueryAction(i);
		return actionArr;
	}

	public void save()
	{
		jEdit.setIntegerProperty(ACTIONS + "size", actionsModel.size());
		for (int i = 0; i < actionsModel.size(); i++)
		{
			QueryAction qa = (QueryAction) actionsModel.getElementAt(i);
			qa.save(i);
		}
		CtagsInterfacePlugin.updateActions();
	}

	static public class ActionEditor extends JDialog
	{
		private QueryAction action;
		private JTextField query;
		private JTextField name;
		private ButtonGroup querytype;
		private List<JRadioButton> buttons = new ArrayList<JRadioButton>();
		private JCheckBox callImmediately;

		public ActionEditor(QueryAction qa)
		{
			super(jEdit.getActiveView(), jEdit.getProperty(MESSAGE +
				"actionEditorTitle"), true);
			setLayout(new GridBagLayout());
			GridBagConstraints c = new GridBagConstraints();
			JPanel p = new JPanel();
			p.add(new JLabel(jEdit.getProperty(MESSAGE + "actionName")));
			name = new JTextField(30);
			p.add(name);
			p.setAlignmentX(LEFT_ALIGNMENT);
			c.anchor = GridBagConstraints.NORTHWEST;
			c.gridx = c.gridy = 0;
			c.gridwidth = c.gridheight = 1;
			add(p, c);

			p = new JPanel();
			p.add(new JLabel(jEdit.getProperty(MESSAGE + "luceneQuery")));
			query = new JTextField(60);
			p.add(query);
			p.setAlignmentX(LEFT_ALIGNMENT);
			c.gridy++;
			add(p, c);

			p = new JPanel();
			p.add(new JLabel(jEdit.getProperty(MESSAGE + "queryType")));
			querytype = new ButtonGroup();
			for (QueryAction.QueryType type: QueryAction.QueryType.values())
			{
				JRadioButton b = new JRadioButton(type.text);
				b.setActionCommand(type.toString());
				querytype.add(b);
				if (type == QueryAction.QueryType.JUMP_TO_TAG)
					b.setSelected(true);
				p.add(b);
				buttons.add(b);
			}
			p.setAlignmentX(LEFT_ALIGNMENT);
			c.gridy++;
			add(p, c);

			p = new JPanel();
			p.add(new JLabel(jEdit.getProperty(MESSAGE + "callImmediately")));
			callImmediately = new JCheckBox();
			p.add(callImmediately);
			p.setAlignmentX(LEFT_ALIGNMENT);
			c.gridy++;
			add(p, c);


			p = new JPanel();
			JButton ok = new JButton("Ok");
			p.add(ok);
			JButton cancel = new JButton("Cancel");
			p.add(cancel);
			p.setAlignmentX(LEFT_ALIGNMENT);
			c.gridy++;
			add(p, c);

			ok.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent ae)
				{
					QueryType type = QueryType.JUMP_TO_TAG;
					for (JRadioButton b : buttons)
					{
						if (b.isSelected())
						{
							type = QueryType.valueOf(b.getActionCommand());
							break;
						}
					}
					action = new QueryAction(name.getText(), query.getText(),
						type, callImmediately.isSelected());
					dispose();
				}
			});
			cancel.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent ae)
				{
					dispose();
				}
			});
			action = qa;
			if (action != null)
			{
				name.setText(action.getName());
				query.setText(action.getQuery());
				QueryType type = action.getQueryType();
				for (JRadioButton b : buttons)
					b.setSelected(
						QueryType.valueOf(b.getActionCommand()) == type);
				callImmediately.setSelected(action.isShowImmediately());
			}
			pack();
			setLocationRelativeTo(null);
			setVisible(true);
		}

		public ActionEditor()
		{
			this(null);
		}

		public QueryAction getAction()
		{
			return action;
		}
	}

}
