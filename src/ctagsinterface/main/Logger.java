package ctagsinterface.main;

import ctagsinterface.dockables.Progress;

public class Logger
{
	private String name;
	private Progress progress;

	public Logger(String name, Progress progress)
	{
		this.name = name;
		this.progress = progress;
	}

	public String name()
	{
		return name;
	}

	public void beginTask(String task)
	{
		progress.beginTask(this, task);
	}
	public void endTask()
	{
		progress.endTask(this);
	}

	public void log(String s)
	{
		progress.add(this, s);
	}
	public void setProgressParams(int min, int max)
	{
		progress.setProgressParams(this, min, max);
	}
	public void setProgress(int value)
	{
		progress.setProgress(this, value);
	}
}
