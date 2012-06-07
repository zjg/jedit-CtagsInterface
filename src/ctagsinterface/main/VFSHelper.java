package ctagsinterface.main;

import java.awt.Component;
import java.io.IOException;
import java.util.Vector;

import org.gjt.sp.jedit.io.VFS;
import org.gjt.sp.jedit.io.VFSFile;
import org.gjt.sp.jedit.io.VFSManager;

public class VFSHelper {
	static public String getArchiveVFSPath(String archive) {
		return "archive:" + archive + "!";
	}
	static public VFS getArchiveVFS(String archive) {
		return VFSManager.getVFSForPath(getArchiveVFSPath(archive));
	}
	static public boolean checkArchiveVFS(String archive) {
		VFS vfs = getArchiveVFS(archive);
		String path = getArchiveVFSPath(archive);
		Component c = null;
		Object session = vfs.createVFSSession(path, c);
		try {
			VFSFile [] directory = vfs._listFiles(session, path, c);
			if (directory == null)
				return false;
			vfs._endVFSSession(session, c);
		} catch (IOException e1) {
			return false;
		}
		return true;
	}

	/**
	 * Checks whether the file extension of the path provided matches the
	 * extension provided
	 * @param String path to tagFile
	 * @param String extension to match
	 * @return boolean true if path extension equals extension
	 */
	static public boolean checkFileExtension(String filePath, String ext) {
		String fileName = getFileName(filePath);
		String[] fileSplit = fileName.split("\\.");
		if(fileSplit.length>=1) {
			if(fileSplit[fileSplit.length-1].equals(ext))
				return true;
		}
		return false;
	}

	static public void listArchive(Object session, Component c,
		String path, Vector<String> list, boolean filesOnly)
	{
		VFS vfs = VFSManager.getVFSForPath(path);
		if (vfs == null)
			return;
		try {
			VFSFile [] directory = vfs._listFiles(session, path, c);
			if (directory == null) {
				// File entry
				list.add(path);
				return;
			}
			// Directory entry
			if (! filesOnly)
				list.add(path);
			for (VFSFile f: directory) {
				String childPath = f.getPath();
				listArchive(session, c, childPath, list, filesOnly);
			}
		} catch (IOException e) {
		}
	}
	static public Vector<String> listArchive(String archive, boolean filesOnly) {
		VFS vfs = getArchiveVFS(archive);
		if (vfs == null)
			return null;
		Vector<String> files = new Vector<String>();
		String path = getArchiveVFSPath(archive);
		Component c = null;
		Object session = vfs.createVFSSession(path, c);
		listArchive(session, c, path, files, filesOnly);
		try {
			vfs._endVFSSession(session, c);
		} catch (IOException e) {
		}
		return files;
	}
	static public String getFileName(String vfsPath) {
		VFS vfs = VFSManager.getVFSForPath(vfsPath);
		if (vfs == null)
			return null;
		return vfs.getFileName(vfsPath);
	}
	static public void copy(String vfsPath, String localPath) {
		try {
			VFS.copy(null, vfsPath, localPath, null, false);
		} catch (IOException e) {
		}
	}
}
