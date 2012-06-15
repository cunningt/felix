package aQute.bnd.differ;

import static aQute.bnd.service.diff.Delta.*;

import java.util.*;

import aQute.bnd.service.diff.*;

/**
 * A DiffImpl class compares a newer Element to an older Element. The Element
 * classes hide all the low level details. A Element class is either either
 * Structured (has children) or it is a Leaf, it only has a value. The
 * constructor will first build its children (if any) and then calculate the
 * delta. Each comparable element is translated to an Element. If necessary the
 * Element can be sub classed to provide special behavior.
 */

public class DiffImpl implements Diff, Comparable<DiffImpl> {

	final Tree				older;
	final Tree				newer;
	final Collection<DiffImpl>	children;
	final Delta					delta;

	/**
	 * The transitions table defines how the state is escalated depending on the
	 * children. horizontally is the current delta and this is indexed with the
	 * child delta for each child. This escalates deltas from below up.
	 */
	final static Delta[][]		TRANSITIONS	= {
			{
			IGNORED, UNCHANGED, CHANGED, MICRO, MINOR, MAJOR
			}, // IGNORED
			{
			IGNORED, UNCHANGED, CHANGED, MICRO, MINOR, MAJOR
			}, // UNCHANGED
			{
			IGNORED, CHANGED, CHANGED, MICRO, MINOR, MAJOR
			}, // CHANGED
			{
			IGNORED, MICRO, MICRO, MICRO, MINOR, MAJOR
			}, // MICRO
			{
			IGNORED, MINOR, MINOR, MINOR, MINOR, MAJOR
			}, // MINOR
			{
			IGNORED, MAJOR, MAJOR, MAJOR, MAJOR, MAJOR
			}, // MAJOR
			{
			IGNORED, MAJOR, MAJOR, MAJOR, MAJOR, MAJOR
			}, // REMOVED
			{
			IGNORED, MINOR, MINOR, MINOR, MINOR, MAJOR
			}, // ADDED
											};

	/**
	 * Compares the newer against the older, traversing the children if
	 * necessary.
	 * 
	 * @param newer
	 *            The newer Element
	 * @param older
	 *            The older Element
	 * @param types
	 */
	public DiffImpl(Tree newer, Tree older) {
		assert newer != null || older != null;
		this.older = older;
		this.newer = newer;

		// Either newer or older can be null, indicating remove or add
		// so we have to be very careful.
		Tree[] newerChildren = newer == null ? Element.EMPTY : newer.getChildren();
		Tree[] olderChildren = older == null ? Element.EMPTY : older.getChildren();

		int o = 0;
		int n = 0;
		List<DiffImpl> children = new ArrayList<DiffImpl>();
		while (true) {
			Tree nw = n < newerChildren.length ? newerChildren[n] : null;
			Tree ol = o < olderChildren.length ? olderChildren[o] : null;
			DiffImpl diff;

			if (nw == null && ol == null)
				break;

			if (nw != null && ol != null) {
				// we have both sides
				int result = nw.compareTo(ol);
				if (result == 0) {
					// we have two equal named elements
					// use normal diff
					diff = new DiffImpl(nw, ol);
					n++;
					o++;
				} else if (result > 0) {
					// we newer > older, so there is no newer == removed
					diff = new DiffImpl(null, ol);
					o++;
				} else {
					// we newer < older, so there is no older == added
					diff = new DiffImpl(nw, null);
					n++;
				}
			} else {
				// we reached the end of one of the list
				diff = new DiffImpl(nw, ol);
				n++;
				o++;
			}
			children.add(diff);
		}

		// make sure they're read only
		this.children = Collections.unmodifiableCollection(children);
		delta = getDelta(null);
	}

	/**
	 * Return the absolute delta. Also see
	 * {@link #getDelta(aQute.bnd.service.diff.Diff.Ignore)} that allows you to
	 * ignore Diff objects on the fly (and calculate their parents accordingly).
	 */
	public Delta getDelta() {
		return delta;
	}

	/**
	 * This getDelta calculates the delta but allows the caller to ignore
	 * certain Diff objects by calling back the ignore call back parameter. This
	 * can be useful to ignore warnings/errors.
	 */

	public Delta getDelta(Ignore ignore) {

		// If ignored, we just return ignore.
		if (ignore != null && ignore.contains(this))
			return IGNORED;

		if (newer == null) {
			return REMOVED;
		} else if (older == null) {
			return ADDED;
		} else {
			// now we're sure newer and older are both not null
			assert newer != null && older != null;
			assert newer.getClass() == older.getClass();

			Delta local = Delta.UNCHANGED;

			for (DiffImpl child : children) {
				Delta sub = child.getDelta(ignore);
				if (sub == REMOVED)
					sub = child.older.ifRemoved();
				else if (sub == ADDED)
					sub = child.newer.ifAdded();

				// The escalate method is used to calculate the default
				// transition in the
				// delta based on the children. In general the delta can
				// only escalate, i.e.
				// move up in the chain.

				local = TRANSITIONS[sub.ordinal()][local.ordinal()];
			}
			return local;
		}
	}

	public Type getType() {
		return (newer == null ? older : newer).getType();
	}

	public String getName() {
		return (newer == null ? older : newer).getName();
	}

	public Collection< ? extends Diff> getChildren() {
		return children;
	}

	public String toString() {
		return String.format("%-10s %-10s %s", getDelta(), getType(), getName());
	}

	public boolean equals(Object other) {
		if (other instanceof DiffImpl) {
			DiffImpl o = (DiffImpl) other;
			return getDelta() == o.getDelta() && getType() == o.getType() && getName().equals(o.getName());
		}
		return false;
	}

	public int hashCode() {
		return getDelta().hashCode() ^ getType().hashCode() ^ getName().hashCode();
	}

	public int compareTo(DiffImpl other) {
		if (getDelta() == other.getDelta()) {
			if (getType() == other.getType()) {
				return getName().compareTo(other.getName());
			} else
				return getType().compareTo(other.getType());
		} else
			return getDelta().compareTo(other.getDelta());
	}

	public Diff get(String name) {
		for (DiffImpl child : children) {
			if (child.getName().equals(name))
				return child;
		}
		return null;
	}

	public Tree getOlder() {
		return older;
	}

	public Tree getNewer() {
		return newer;
	}

	public Data serialize() {
		Data data = new Data();
		data.type = getType();
		data.delta = delta;
		data.name = getName();
		data.children = new Data[children.size()];
		
		int i=0;		
		for ( Diff d : children)
			data.children[i++] = d.serialize();
				
		return data;
	}


}
