package ctagsinterface.index;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.Vector;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Fieldable;

import ctagsinterface.index.TagIndex.DocHandler;
import ctagsinterface.main.CtagsInterfacePlugin;

@SuppressWarnings("serial")
public class QueryDialog extends JFrame {

	private JTextField query;
	private DefaultTableModel model;
	private JTable table;

	public QueryDialog(JFrame parent)
	{
		setTitle("Query Dialog");
		setLayout(new BorderLayout());
		JPanel p = new JPanel();
		add(p, BorderLayout.NORTH);
		p.setLayout(new BorderLayout());
		p.add(new JLabel("Query:"), BorderLayout.WEST);
		query = new JTextField();
		p.add(query, BorderLayout.CENTER);
		model = new DefaultTableModel();
		table = new JTable(model);
		add(new JScrollPane(table), BorderLayout.CENTER);
		table.setAutoCreateRowSorter(true);
		final JButton go = new JButton("Search");
		p.add(go, BorderLayout.EAST);
		go.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				performQuery();
			}
		});
		query.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				if (e.getKeyCode() == KeyEvent.VK_ENTER)
					go.doClick();
			}
		});
		pack();
		setVisible(true);
	}

	void performQuery()
	{
		model.setRowCount(0);
		final Vector<String> columns = new Vector<String>();
		final Vector<HashMap<String, String>> data = new Vector<HashMap<String, String>>();
		CtagsInterfacePlugin.getIndex().runQuery(query.getText(),
			1000000, new DocHandler()
		{
			public void handle(Document doc)
			{
				HashMap<String, String> values = new HashMap<String, String>();
				data.add(values);
				for (Fieldable f: doc.getFields())
				{
					String name = f.name();
					String value = f.stringValue();
					if (! columns.contains(name))
						columns.add(name);
					values.put(name, value);
				}
			}
		});
		model.setColumnIdentifiers(columns);
		HashMap<String, Integer> columnIndex = new HashMap<String, Integer>();
		int index = 0;
		for (String col: columns)
			columnIndex.put(col, Integer.valueOf(index++));
		for (HashMap<String, String> row: data)
		{
			String [] values = new String[columns.size()];
			for (String name: row.keySet())
				values[columnIndex.get(name).intValue()] = row.get(name);
			model.addRow(values);
		}
	}
}
