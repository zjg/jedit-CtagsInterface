package ctagsinterface.main;

import java.util.Hashtable;
import java.util.Set;

import javax.swing.ImageIcon;

public class Tag {
 
	private String name;
	private String file;
	private int line;
	private String pattern;
	private String kind;
	private Hashtable<String, String> extensions;
	private Hashtable<String, String> attachments;
	static private String LINE_KEY = String.valueOf("line");
	static private String KIND_KEY = String.valueOf("kind");
	static private String [] namespaceExtensions = new String [] {
		"class", "struct", "union", "interface"
	};

	public Tag(String name, String file, String pattern) {
		this.name = name;
		this.file = file;
		this.pattern = pattern;
	}
	public void setExtensions(Hashtable<String, String> extensions) {
		this.extensions = extensions;
		kind = extensions.containsKey(KIND_KEY) ? extensions.get(KIND_KEY) : null; 
		line = extensions.containsKey(LINE_KEY) ? Integer.valueOf(extensions.get(LINE_KEY)) : -1;
	}
	public void setAttachments(Hashtable<String, String> attachments) {
		this.attachments = attachments;
	}
	public String getName() {
		return name;
	}
	public String getFile() {
		return file;
	}
	public String getPattern() {
		return pattern;
	}
	public int getLine() {
		return line;
	}
	public String getKind() {
		return kind;
	}
	public String getExtension(String name) {
		return extensions.get(name);
	}
	public Set<String> getExtensions() {
		return extensions.keySet();
	}
	public Set<String> getAttachments() {
		return attachments.keySet();
	}
	public String getAttachment(String name) {
		return attachments.get(name);
	}
	public ImageIcon getIcon() {
		return CtagsInterfacePlugin.getIcon(this);
	}
	public String getNamespace() {
		for (int i = 0; i < namespaceExtensions.length; i++) {
			String ext = getExtension(namespaceExtensions[i]);
			if (ext != null)
				return ext;
		}
		return null;
	}
	public String getQualifiedName() {
		String ns = getNamespace();
		if (ns == null)
			return getName();
		return "(" + ns + ") " + getName();
	}
	public void setFile(String file) {
		this.file = file;
	}
}
