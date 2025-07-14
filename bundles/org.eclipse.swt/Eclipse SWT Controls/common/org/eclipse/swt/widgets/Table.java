/*******************************************************************************
 * Copyright (c) 2000, 2022 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Roland Oldenburg <r.oldenburg@hsp-software.de> - Bug 292199
 *     Conrad Groth - Bug 384906
 *     Denis Ungemach (SAP) - custom-draw rewrite
 *     Thomas Singer (syntevo) - fine-tuning
 *******************************************************************************/
package org.eclipse.swt.widgets;

import java.util.*;
import java.util.List;

import org.eclipse.swt.*;
import org.eclipse.swt.accessibility.*;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.internal.*;

/**
 * Instances of this class implement a selectable user interface object that
 * displays a list of images and strings and issues notification when selected.
 * <p>
 * The item children that may be added to instances of this class must be of
 * type <code>TableItem</code>.
 * </p>
 * <p>
 * Style <code>VIRTUAL</code> is used to create a <code>Table</code> whose
 * <code>TableItem</code>s are to be populated by the client on an on-demand
 * basis instead of up-front. This can provide significant performance
 * improvements for tables that are very large or for which
 * <code>TableItem</code> population is expensive (for example, retrieving
 * values from an external source).
 * </p>
 * <p>
 * Here is an example of using a <code>Table</code> with style
 * <code>VIRTUAL</code>:
 * </p>
 *
 * <pre>
 * <code>
 *  final Table table = new Table (parent, SWT.VIRTUAL | SWT.BORDER);
 *  table.setItemCount (1000000);
 *  table.addListener (SWT.SetData, new Listener () {
 *      public void handleEvent (Event event) {
 *          TableItem item = (TableItem) event.item;
 *          int index = table.indexOf (item);
 *          item.setText ("Item " + index);
 *          System.out.println (item.getText ());
 *      }
 *  });
 * </code>
 * </pre>
 * <p>
 * Note that although this class is a subclass of <code>Composite</code>, it
 * does not normally make sense to add <code>Control</code> children to it, or
 * set a layout on it, unless implementing something like a cell editor.
 * </p>
 * <dl>
 * <dt><b>Styles:</b></dt>
 * <dd>SINGLE, MULTI, CHECK, FULL_SELECTION, HIDE_SELECTION, VIRTUAL,
 * NO_SCROLL</dd>
 * <dt><b>Events:</b></dt>
 * <dd>Selection, DefaultSelection, SetData, MeasureItem, EraseItem,
 * PaintItem</dd>
 * </dl>
 * <p>
 * Note: Only one of the styles SINGLE, and MULTI may be specified.
 * </p>
 * <p>
 * IMPORTANT: This class is <em>not</em> intended to be subclassed.
 * </p>
 *
 * @see <a href="http://www.eclipse.org/swt/snippets/#table">Table, TableItem,
 *      TableColumn snippets</a>
 * @see <a href="http://www.eclipse.org/swt/examples.php">SWT Example:
 *      ControlExample</a>
 * @see <a href="http://www.eclipse.org/swt/">Sample code and further
 *      information</a>
 * @noextend This class is not intended to be subclassed by clients.
 */
public class Table extends CustomComposite {

// ------------------------------------------------------------

	static final boolean LOG_NOT_IMPLEMENTED = false; // write to console, if method calls are not implemented: default
														// false

// ------------------------------------------------------------

	private static final int DRAW_FLAGS = SWT.DRAW_MNEMONIC | SWT.DRAW_TAB | SWT.DRAW_TRANSPARENT | SWT.DRAW_DELIMITER;

	static final Color SELECTION_COLOR = new Color(224, 238, 254);
	static final Color HOVER_COLOR = new Color(234, 244, 255);

	static final int TABLE_INITIAL_RIGHT_SHIFT = 3;
	static final int TABLE_CHECKBOX_RIGHT_SHIFT = 35;

	static final int TABLE_GRID_LINE_SIZE = 1;

	private final List<TableItem> itemsList = new ArrayList<>();
	private final TreeMap<Integer, TableItem> virtualItemsList = new TreeMap<>();
	private final ListLikeModel selectionModel;
	// TODO implement focusHandling
	private TableItem focusItem;
	Item mouseHoverElement;
	private final List<TableColumn> columnsList = new ArrayList<>();

	private final TableColumnsHandler columnsHandler = new TableColumnsHandler(this);
	private final TableItemsHandler itemsHandler = new TableItemsHandler(this);

	TableItem currentItem;
	TableColumn sortColumn;
	Rectangle focusRect;
	boolean[] columnVisible;
	long headerToolTipHandle, hwndHeader, itemToolTipHandle;
	boolean ignoreCustomDraw, ignoreDrawForeground, ignoreDrawBackground, ignoreDrawFocus, ignoreDrawSelection,
			ignoreDrawHot;
	boolean customDraw, dragStarted, explorerTheme, firstColumnImage, fixScrollWidth, tipRequested, wasSelected,
			wasResized, painted;
	boolean ignoreActivate, ignoreSelect, ignoreShrink, ignoreResize, ignoreColumnMove, ignoreColumnResize,
			fullRowSelect, settingItemHeight;
	boolean headerItemDragging;
	int itemHeight, lastIndexOf, lastWidth, sortDirection, resizeCount, selectionForeground, hotIndex;
	static /* final */ long HeaderProc;
	static final int INSET = 4;
	static final int GRID_WIDTH = 1;
	static final int SORT_WIDTH = 10;
	static final int HEADER_MARGIN = 12;
	static final int HEADER_EXTRA = 3;
	static final int VISTA_EXTRA = 2;
	static final int EXPLORER_EXTRA = 2;
	static final int H_SCROLL_LIMIT = 32;
	static final int V_SCROLL_LIMIT = 16;
	static final int DRAG_IMAGE_SIZE = 301;

	static boolean COMPRESS_ITEMS = true;

	private Accessible acc;
	private AccessibleAdapter accAdapter;

	private boolean headerVisible;

	private int[] columnOrder;

	private final TableRenderer renderer;

	private Color headerBackgroundColor;

	// TODO implement
	private boolean linesVisible;

	private Color headerForegroundColor;

	private int virtualItemCount;

	/**
	 * Constructs a new instance of this class given its parent and a style value
	 * describing its behavior and appearance.
	 * <p>
	 * The style value is either one of the style constants defined in class
	 * <code>SWT</code> which is applicable to instances of this class, or must be
	 * built by <em>bitwise OR</em>'ing together (that is, using the
	 * <code>int</code> "|" operator) two or more of those <code>SWT</code> style
	 * constants. The class description lists the style constants that are
	 * applicable to the class. Style bits are also inherited from superclasses.
	 * </p>
	 *
	 * @param parent a composite control which will be the parent of the new
	 *               instance (cannot be null)
	 * @param style  the style of control to construct
	 *
	 * @exception IllegalArgumentException
	 *                                     <ul>
	 *                                     <li>ERROR_NULL_ARGUMENT - if the parent
	 *                                     is null</li>
	 *                                     </ul>
	 * @exception SWTException
	 *                                     <ul>
	 *                                     <li>ERROR_THREAD_INVALID_ACCESS - if not
	 *                                     called from the thread that created the
	 *                                     parent</li>
	 *                                     <li>ERROR_INVALID_SUBCLASS - if this
	 *                                     class is not an allowed subclass</li>
	 *                                     </ul>
	 *
	 * @see SWT#SINGLE
	 * @see SWT#MULTI
	 * @see SWT#CHECK
	 * @see SWT#FULL_SELECTION
	 * @see SWT#HIDE_SELECTION
	 * @see SWT#VIRTUAL
	 * @see SWT#NO_SCROLL
	 * @see Widget#checkSubclass
	 * @see Widget#getStyle
	 */
	public Table(Composite parent, int style) {
		super(parent, checkStyle(style));

		selectionModel = new ListLikeModel((style & SWT.SINGLE) != 0);

		renderer = new DefaultTableRenderer(this);

		initialize();
	}

	private void initialize() {
		final Listener listener = event -> {
			switch (event.type) {
			case SWT.MouseDown -> onMouseDown(event);
			case SWT.MouseUp -> onMouseUp(event);
			case SWT.Paint -> onPaint(event);
			case SWT.Resize -> onResize();
			case SWT.FocusIn -> onFocusIn();
			case SWT.FocusOut -> onFocusOut();
			case SWT.Traverse -> onTraverse(event);
			case SWT.Selection -> onSelection(event);
			case SWT.MouseWheel -> onScrollBar();
			case SWT.KeyDown -> onKeyDown(event);
			case SWT.KeyUp -> onKeyUp(event);
			case SWT.MouseMove -> onMouseMove(event);
			case SWT.MouseExit -> onMouseExit();
			case SWT.MouseDoubleClick -> onDoubleClick(event);
			}
		};

		addListener(SWT.KeyDown, listener);
		addListener(SWT.KeyUp, listener);
		addListener(SWT.MouseMove, listener);
		addListener(SWT.MouseExit, listener);
		addListener(SWT.MouseDown, listener);
		addListener(SWT.MouseUp, listener);
		addListener(SWT.Paint, listener);
		addListener(SWT.Resize, listener);
		addListener(SWT.FocusIn, listener);
		addListener(SWT.FocusOut, listener);
		addListener(SWT.Traverse, listener);
		addListener(SWT.MouseWheel, listener);
		addListener(SWT.MouseDoubleClick, listener);

		if (verticalBar != null) {
			verticalBar.addListener(SWT.Selection, listener);
		}
		if (horizontalBar != null) {
			horizontalBar.addListener(SWT.Selection, listener);
		}

		initializeAccessible();
	}

	private void onMouseExit() {
		if (mouseHoverElement != null) {
			mouseHoverElement = null;
			redraw();
		}
	}

	private void onMouseMove(Event event) {
		columnsHandler.handleMouseMove(event);
		itemsHandler.handleMouseMove(event);
	}

	private void onScrollBar() {
		ScrollBar scrollBar = getVerticalBar();
		if (scrollBar != null) {
			int selection = scrollBar.getSelection();
			setTopIndex(selection);
		}
	}

	private void initializeAccessible() {
		acc = getAccessible();

		accAdapter = new AccessibleAdapter() {
			@Override
			public void getName(AccessibleEvent e) {
				// TODO implement accessibility
				e.result = "text";
			}

			@Override
			public void getHelp(AccessibleEvent e) {
				e.result = getToolTipText();
			}

			@Override
			public void getKeyboardShortcut(AccessibleEvent e) {
			}
		};
		acc.addAccessibleListener(accAdapter);
		addListener(SWT.FocusIn, event -> acc.setFocus(ACC.CHILDID_SELF));
	}

	private void onSelection(Event event) {
		if (event.widget == verticalBar) {
			setTopIndex(verticalBar.getSelection());
		}

		updateColumnsX();
		// TODO also the scrollbars will be handled here

		redraw();
	}

	void updateColumnsX() {
		int x = -horizontalBar.getSelection();
		final int[] columnOrder = getColumnOrder();
		for (int i : columnOrder) {
			final TableColumn column = columnsList.get(i);
			column.setX(x);
			x += column.getWidth();
		}
	}

	private void onTraverse(Event event) {
	}

	private void onFocusIn() {
		redraw();
	}

	private void onFocusOut() {
		redraw();
	}

	private void onKeyDown(Event event) {
		if (!event.doit) {
			return;
		}
		final int itemCount = getItemCount();
		if (itemCount == 0) {
			return;
		}

		final boolean shiftPressed = (event.stateMask & SWT.SHIFT) != 0;
		final boolean ctrlOrCmdPressed = (event.stateMask & SWT.MOD1) != 0;
		switch (event.keyCode) {
			case SWT.HOME -> {
				selectionModel.moveSelectionAbsolute(0, shiftPressed, ctrlOrCmdPressed);
				scrollIntoView();
				redraw();
				event.doit = false;
			}
			case SWT.END -> {
				selectionModel.moveSelectionAbsolute(itemCount - 1, shiftPressed, ctrlOrCmdPressed);
				scrollIntoView();
				redraw();
				event.doit = false;
			}
			case SWT.ARROW_UP -> {
				selectionModel.moveSelectionRelative(-1, shiftPressed, ctrlOrCmdPressed);
				scrollIntoView();
				redraw();
				event.doit = false;
			}
			case SWT.ARROW_DOWN -> {
				selectionModel.moveSelectionRelative(1, shiftPressed, ctrlOrCmdPressed);
				scrollIntoView();
				redraw();
				event.doit = false;
			}
			case SWT.PAGE_UP -> {
				final int amount = Math.max(1, getFullyVisibleItemCount());
				selectionModel.moveSelectionRelative(-amount, shiftPressed, ctrlOrCmdPressed);
				scrollIntoView();
				redraw();
				event.doit = false;
			}
			case SWT.PAGE_DOWN -> {
				final int amount = Math.max(1, getFullyVisibleItemCount());
				selectionModel.moveSelectionRelative(amount, shiftPressed, ctrlOrCmdPressed);
				scrollIntoView();
				redraw();
				event.doit = false;
			}
		}
	}

	private void scrollIntoView() {
		final int current = selectionModel.getCurrent();
		if (current < 0) {
			return;
		}
		final int topIndex = selectionModel.getTopIndex();
		final int fullyVisibleItemCount = getFullyVisibleItemCount();
		final int margin = Math.min(fullyVisibleItemCount / 2, 3);
		final int lastFullyVisibleItem = fullyVisibleItemCount + topIndex;
		if (current < topIndex + margin) {
			selectionModel.setTopIndex(Math.max(0, current - margin));
		} else if (current > lastFullyVisibleItem - margin) {
			selectionModel.setTopIndex(Math.min(current - fullyVisibleItemCount + margin,
					selectionModel.getCount() - fullyVisibleItemCount));
		}
	}

	private int getFullyVisibleItemCount() {
		int height = getClientArea().height;
		if (headerVisible) {
			height -= getHeaderHeight();
		}
		final int itemHeight = getItemHeight();
		return Math.max(0, height / itemHeight);
	}

	private void onKeyUp(Event event) {
	}

	private void onResize() {
		if (ignoreResize) {
			return;
		}

		ignoreResize = true;
		try {
			updateScrollBarWithTextSize();
			redraw();
		}
		finally {
			ignoreResize = false;
		}
	}

	void updateScrollBarWithTextSize() {
		final Point size = getSize();
		if (size.x == 0 || size.y == 0) {
			return;
		}

		updateVerticalScrollBar();

		if (horizontalBar != null) {
			Point tableSize = getPreferredSize();
			Rectangle ca = getClientArea();
			// +1 for the closing vertical line of the table header
			horizontalBar.setMaximum(tableSize.x + 1);
			horizontalBar.setMinimum(0);
			horizontalBar.setThumb(ca.width);
			horizontalBar.setVisible(tableSize.x > ca.width);
		}
	}

	private void updateVerticalScrollBar() {
		if (verticalBar == null) {
			return;
		}

		final int fullyVisibleItemCount = getFullyVisibleItemCount();

		final int itemCount = getItemCount();
		if (itemCount > fullyVisibleItemCount) {
			verticalBar.setVisible(true);
			verticalBar.setValues(selectionModel.getTopIndex(), 0, itemCount, fullyVisibleItemCount, 1, fullyVisibleItemCount);
		} else {
			verticalBar.setVisible(false);
			verticalBar.setValues(0, 0, 0, 1, 1, 1);
		}
	}

	@Override
	void releaseChildren(boolean destroy) {
		super.releaseChildren(destroy);

		Set<TableColumn> columnsSet = new HashSet<>();
		Set<TableItem> itemsSet = new HashSet<>();

		columnsSet.addAll(columnsList);
		itemsSet.addAll(itemsList);
		itemsSet.addAll(virtualItemsList.values());

		itemsList.clear();
		columnsList.clear();
		virtualItemsList.clear();
		virtualItemCount = 0;

		for (TableColumn c : columnsSet) {
			c.dispose();
		}

		for (TableItem i : itemsSet) {
			i.dispose();
		}
	}

	private void onMouseDown(Event e) {
		Point p = new Point(e.x, e.y);

		if (columnsHandler.handleMouseDown(e)
		    || !itemsHandler.getItemsClientArea().contains(e.x, e.y)) {
			return;
		}

		for (int i = getTopIndex(); i <= itemsHandler.getLastVisibleElementIndex(); i++) {
			TableItem it = getItem(i);

			Rectangle b = it.getBounds();
			if (it.isInCheckArea(p)) {
				it.toggleCheck();
				break;
			}
			if (b.contains(p)) {
				final boolean shiftPressed = (e.stateMask & SWT.SHIFT) != 0;
				final boolean ctrlOrCmdPressed = (e.stateMask & SWT.MOD1) != 0;
				clickAtRow(i, shiftPressed, ctrlOrCmdPressed);
				break;
			}
		}
		redraw();
		sendSelectionEvent(SWT.Selection);
	}

	private void onDoubleClick(Event event) {
		if (!columnsHandler.handleMouseDown(event)) {
			itemsHandler.handleDoubleClick(event);
		}
	}

	private void clickAtRow(int index, boolean shiftPressed, boolean ctrlOrCmdPressed) {
		final boolean multiSelection = (style & SWT.MULTI) != 0;
		if (multiSelection) {
			if (ctrlOrCmdPressed) {
				selectionModel.toggleSelection(index);
				return;
			}
			if (shiftPressed) {
				final int anchor = selectionModel.getAnchor();
				if (anchor >= 0) {
					selectionModel.selectRangeTo(index);
					return;
				}
			}
		}

		selectionModel.setSelection(index);
	}

	private void onMouseUp(Event e) {
		if (columnsHandler.getHeaderBounds().contains(e.x, e.y) || columnsHandler.isColumnResizeActive()) {
			columnsHandler.handleMouseUp(e);
		}
	}

	@Override
	void _addListener(int eventType, Listener listener) {
		super._addListener(eventType, listener);
		switch (eventType) {
		case SWT.MeasureItem:
		case SWT.EraseItem:
		case SWT.PaintItem:
			setCustomDraw(true);
			setBackgroundTransparent(true);
			break;
		}
	}

	private void onPaint(Event event) {
		updateColumnsX();
		renderer.paint(event.gc);
	}

	void redrawColumnHeader(TableColumn column) {
		if (!getHeaderVisible()) {
			return;
		}
		int width = column.getWidth();
		int height = getHeaderHeight();
		redraw(column.getX(), 0, width, height, true);
	}

	public boolean columnsExist() {
		return !columnsList.isEmpty();
	}

	private TableItem _getItem(int index) {
		return _getItem(index, true);
	}

	TableItem _getItem(int index, boolean create) {
		return _getItem(index, create, -1);
	}

	private TableItem _getItem(int index, boolean create, int count) {
		if (isVirtual()) {
			if (index < virtualItemCount) {
				TableItem e = virtualItemsList.get(index);
				if (e == null && create) {
					e = new TableItem(this, SWT.None, index);
				}
				return e;
			}
			error(SWT.ERROR_INVALID_RANGE);
		}

		if (index >= getItemCount()) error(SWT.ERROR_INVALID_RANGE);

		return itemsList.get(index);
	}

	/**
	 * Adds the listener to the collection of listeners who will be notified when
	 * the user changes the receiver's selection, by sending it one of the messages
	 * defined in the <code>SelectionListener</code> interface.
	 * <p>
	 * When <code>widgetSelected</code> is called, the item field of the event
	 * object is valid. If the receiver has the <code>SWT.CHECK</code> style and the
	 * check selection changes, the event object detail field contains the value
	 * <code>SWT.CHECK</code>. <code>widgetDefaultSelected</code> is typically
	 * called when an item is double-clicked. The item field of the event object is
	 * valid for default selection, but the detail field is not used.
	 * </p>
	 *
	 * @param listener the listener which should be notified when the user changes
	 *                 the receiver's selection
	 *
	 * @exception IllegalArgumentException
	 *                                     <ul>
	 *                                     <li>ERROR_NULL_ARGUMENT - if the listener
	 *                                     is null</li>
	 *                                     </ul>
	 * @exception SWTException
	 *                                     <ul>
	 *                                     <li>ERROR_WIDGET_DISPOSED - if the
	 *                                     receiver has been disposed</li>
	 *                                     <li>ERROR_THREAD_INVALID_ACCESS - if not
	 *                                     called from the thread that created the
	 *                                     receiver</li>
	 *                                     </ul>
	 *
	 * @see SelectionListener
	 * @see #removeSelectionListener
	 * @see SelectionEvent
	 */
	public void addSelectionListener(SelectionListener listener) {
		addTypedListener(listener, SWT.Selection, SWT.DefaultSelection);
	}

	private static int checkStyle(int style) {
		/*
		 * Feature in Windows. Even when WS_HSCROLL or WS_VSCROLL is not specified,
		 * Windows creates trees and tables with scroll bars. The fix is to set H_SCROLL
		 * and V_SCROLL.
		 *
		 * NOTE: This code appears on all platforms so that applications have consistent
		 * scroll bar behavior.
		 */
		if ((style & SWT.NO_SCROLL) == 0) {
			style |= SWT.H_SCROLL | SWT.V_SCROLL;
		}
		style |= SWT.NO_BACKGROUND;
		return checkBits(style, SWT.SINGLE, SWT.MULTI, 0, 0, 0, 0);
	}

	@Override
	void checkBuffered() {
		super.checkBuffered();
//		style |= SWT.DOUBLE_BUFFERED;
	}

	boolean checkData(TableItem item, boolean redraw) {
		if ((style & SWT.VIRTUAL) == 0) return true;
		return checkData(item, indexOf(item), redraw);
	}

	boolean checkData(TableItem item, int index, boolean redraw) {
		if ((style & SWT.VIRTUAL) == 0) return true;
		if (!item.cached) {
			item.cached = true;
			Event event = new Event();
			event.item = item;
			event.index = index;
			currentItem = item;
			sendEvent(SWT.SetData, event);
			// widget could be disposed at this point
			currentItem = null;
			if (isDisposed() || item.isDisposed()) return false;
			if (redraw && !setScrollWidth(item, false)) {
				item.redraw();
			}
		}
		return true;
	}

	@Override
	protected void checkSubclass() {
		if (!isValidSubclass()) error(SWT.ERROR_INVALID_SUBCLASS);
	}

	/**
	 * Clears the item at the given zero-relative index in the receiver. The text,
	 * icon and other attributes of the item are set to the default value. If the
	 * table was created with the <code>SWT.VIRTUAL</code> style, these attributes
	 * are requested again as needed.
	 *
	 * @param index the index of the item to clear
	 *
	 * @exception IllegalArgumentException
	 *                                     <ul>
	 *                                     <li>ERROR_INVALID_RANGE - if the index is
	 *                                     not between 0 and the number of elements
	 *                                     in the list minus 1 (inclusive)</li>
	 *                                     </ul>
	 * @exception SWTException
	 *                                     <ul>
	 *                                     <li>ERROR_WIDGET_DISPOSED - if the
	 *                                     receiver has been disposed</li>
	 *                                     <li>ERROR_THREAD_INVALID_ACCESS - if not
	 *                                     called from the thread that created the
	 *                                     receiver</li>
	 *                                     </ul>
	 *
	 * @see SWT#VIRTUAL
	 * @see SWT#SetData
	 *
	 * @since 3.0
	 */
	public void clear(int index) {
		clear(new int[] { index });
	}

	/**
	 * Removes the items from the receiver which are between the given zero-relative
	 * start and end indices (inclusive). The text, icon and other attributes of the
	 * items are set to their default values. If the table was created with the
	 * <code>SWT.VIRTUAL</code> style, these attributes are requested again as
	 * needed.
	 *
	 * @param start the start index of the item to clear
	 * @param end   the end index of the item to clear
	 *
	 * @exception IllegalArgumentException
	 *                                     <ul>
	 *                                     <li>ERROR_INVALID_RANGE - if either the
	 *                                     start or end are not between 0 and the
	 *                                     number of elements in the list minus 1
	 *                                     (inclusive)</li>
	 *                                     </ul>
	 * @exception SWTException
	 *                                     <ul>
	 *                                     <li>ERROR_WIDGET_DISPOSED - if the
	 *                                     receiver has been disposed</li>
	 *                                     <li>ERROR_THREAD_INVALID_ACCESS - if not
	 *                                     called from the thread that created the
	 *                                     receiver</li>
	 *                                     </ul>
	 *
	 * @see SWT#VIRTUAL
	 * @see SWT#SetData
	 *
	 * @since 3.0
	 */
	public void clear(int start, int end) {
		checkWidget();

		int count = getItemCount();

		if (start < 0 || start > count - 1) error(SWT.ERROR_INVALID_RANGE);
		if (end < 0 || end > count - 1) error(SWT.ERROR_INVALID_RANGE);
		if (end < start) error(SWT.ERROR_INVALID_RANGE);

		int[] indices = new int[end - start + 1];
		for (int i = start; i <= end; i++) {
			indices[i - start] = i;
		}

		clear(indices);
	}

	/**
	 * Clears the items at the given zero-relative indices in the receiver. The
	 * text, icon and other attributes of the items are set to their default values.
	 * If the table was created with the <code>SWT.VIRTUAL</code> style, these
	 * attributes are requested again as needed.
	 *
	 * @param indices the array of indices of the items
	 *
	 * @exception IllegalArgumentException
	 *                                     <ul>
	 *                                     <li>ERROR_INVALID_RANGE - if the index is
	 *                                     not between 0 and the number of elements
	 *                                     in the list minus 1 (inclusive)</li>
	 *                                     <li>ERROR_NULL_ARGUMENT - if the indices
	 *                                     array is null</li>
	 *                                     </ul>
	 * @exception SWTException
	 *                                     <ul>
	 *                                     <li>ERROR_WIDGET_DISPOSED - if the
	 *                                     receiver has been disposed</li>
	 *                                     <li>ERROR_THREAD_INVALID_ACCESS - if not
	 *                                     called from the thread that created the
	 *                                     receiver</li>
	 *                                     </ul>
	 *
	 * @see SWT#VIRTUAL
	 * @see SWT#SetData
	 *
	 * @since 3.0
	 */
	public void clear(int[] indices) {
		checkWidget();

		if (indices == null) error(SWT.ERROR_NULL_ARGUMENT);
		if (indices.length == 0) return;

		int count = getItemCount();
		for (int index : indices) {
			if (0 > index || index >= count) error(SWT.ERROR_INVALID_RANGE);

			TableItem item = _getItem(index, false);
			if (item != null) {
				item.clear();
				item.redraw();
			}
		}
	}

	/**
	 * Clears all the items in the receiver. The text, icon and other attributes of
	 * the items are set to their default values. If the table was created with the
	 * <code>SWT.VIRTUAL</code> style, these attributes are requested again as
	 * needed.
	 *
	 * @exception SWTException
	 *                         <ul>
	 *                         <li>ERROR_WIDGET_DISPOSED - if the receiver has been
	 *                         disposed</li>
	 *                         <li>ERROR_THREAD_INVALID_ACCESS - if not called from
	 *                         the thread that created the receiver</li>
	 *                         </ul>
	 *
	 * @see SWT#VIRTUAL
	 * @see SWT#SetData
	 *
	 * @since 3.0
	 */
	public void clearAll() {
		checkWidget();

		if (getItemCount() == 0) return;

		if (isVirtual()) {
			for (Map.Entry<Integer, TableItem> e : virtualItemsList.entrySet()) {
				final TableItem value = e.getValue();
				if (value != null) {
					value.clear();
				}
			}
			return;
		}

		clear(0, getItemCount() - 1);
	}

	@Override
	public Point computeSize(int wHint, int hHint, boolean changed) {
		checkWidget();

		Point defaultSize = getPreferredSize();
		final Rectangle rectangle = computeTrim(0, 0, defaultSize.x, defaultSize.y);
		int width = wHint == SWT.DEFAULT ? rectangle.width : wHint;
		int height = hHint == SWT.DEFAULT ? rectangle.height : hHint;
		return new Point(width, height);
	}

	private Point getPreferredSize() {
		Point headerSize = columnsHandler.getSize();
		Point itemsArea = itemsHandler.getSize();

		int width = Math.max(headerSize.x, itemsArea.x);
		int height = itemsArea.y;
		if (headerVisible) {
			height += headerSize.y;
		}
		return new Point(width, height);
	}

	void createHeaderToolTips() {
		logNotImplemented();
	}

	void destroyItem(TableColumn column) {
		if (!columnsList.contains(column)) return;

		int index = columnsList.indexOf(column);
		columnsList.remove(column);
		moveTextsItemsToLeft(index);

		if (columnOrder != null) {
			int[] newColOrder = new int[columnsList.size()];

			boolean indexOccurred = false;
			for (int i = 0; i < columnOrder.length; i++) {
				int reduce = indexOccurred ? 1 : 0;

				if (columnOrder[i] < index) {
					newColOrder[i - reduce] = columnOrder[i];
				} else if (columnOrder[i] > index) {
					newColOrder[i - reduce] = columnOrder[i] - 1;
				} else {
					indexOccurred = true;
				}
			}

			this.columnOrder = newColOrder;
		}
	}

	void createItem(TableColumn column, int index) {
		columnsList.add(index, column);

		moveTextsItemsToRight(index);

		final TableItem[] items = getItems();
		for (TableItem it : items) {
			it.clearCache();
		}

		if (columnOrder != null) {
			int[] newOrder = new int[columnsList.size()];
			System.arraycopy(columnOrder, 0, newOrder, 0, columnOrder.length);

			for (int i = columnOrder.length; i < columnsList.size(); i++) {
				newOrder[i] = i;
			}

			this.columnOrder = newOrder;
		}

		updateScrollBarWithTextSize();
		redraw();
	}

	private void moveTextsItemsToRight(int index) {
		for (TableItem i : itemsList) {
			i.moveTextToRightAt(index);
		}
	}

	private void moveTextsItemsToLeft(int index) {
		for (TableItem i : itemsList) {
			i.moveTextsItemsToLeft(index);
		}
	}

	protected int getTotalColumnWidth() {
		return columnsHandler.getSize().x;
	}

	protected int getColumnWidth() {
		if (!columnsList.isEmpty()) {
			return columnsList.get(0).getWidth();
		}
		return 0;
	}

	void createItem(TableItem item, int index) {
		if (isVirtual()) {
			TableItem previous = virtualItemsList.get(index);
			if (previous != null) {
				virtualItemsList.remove(index);
				previous.dispose();
			}

			virtualItemsList.put(index, item);
		} else {
			itemsList.add(index, item);

			selectionModel.add(index);
			if (selectionModel.getCount() != itemsList.size()) error(SWT.ERROR_UNSPECIFIED);

			final int topIndex = selectionModel.getTopIndex();
			if (index < topIndex) {
				for (int i = 0; i < index; i++) {
					itemsList.get(i).clearCache();
				}
			} else {
				for (int i = index; i < itemsList.size(); i++) {
					itemsList.get(i).clearCache();
				}
			}
		}

		if (!isVirtual()) {
			updateScrollBarWithTextSize();
		}
		if (index >= getTopIndex() && index <= itemsHandler.getLastVisibleElementIndex()) {
			redraw();
		}
	}

	private boolean customHeaderDrawing() {
		return headerBackgroundColor != null || headerForegroundColor != null;
	}

	/**
	 * Deselects the items at the given zero-relative indices in the receiver. If
	 * the item at the given zero-relative index in the receiver is selected, it is
	 * deselected. If the item at the index was not selected, it remains deselected.
	 * Indices that are out of range and duplicate indices are ignored.
	 *
	 * @param indices the array of indices for the items to deselect
	 *
	 * @exception IllegalArgumentException
	 *                                     <ul>
	 *                                     <li>ERROR_NULL_ARGUMENT - if the set of
	 *                                     indices is null</li>
	 *                                     </ul>
	 * @exception SWTException
	 *                                     <ul>
	 *                                     <li>ERROR_WIDGET_DISPOSED - if the
	 *                                     receiver has been disposed</li>
	 *                                     <li>ERROR_THREAD_INVALID_ACCESS - if not
	 *                                     called from the thread that created the
	 *                                     receiver</li>
	 *                                     </ul>
	 */
	public void deselect(int[] indices) {
		checkWidget();

		if (itemsList.isEmpty()) return;
		if (indices == null) error(SWT.ERROR_NULL_ARGUMENT);
		if (indices.length == 0) return;

		if (selectionModel.deselect(indices)) {
			redraw();
		}
	}

	/**
	 * Deselects the item at the given zero-relative index in the receiver. If the
	 * item at the index was already deselected, it remains deselected. Indices that
	 * are out of range are ignored.
	 *
	 * @param index the index of the item to deselect
	 *
	 * @exception SWTException
	 *                         <ul>
	 *                         <li>ERROR_WIDGET_DISPOSED - if the receiver has been
	 *                         disposed</li>
	 *                         <li>ERROR_THREAD_INVALID_ACCESS - if not called from
	 *                         the thread that created the receiver</li>
	 *                         </ul>
	 */
	public void deselect(int index) {
		checkWidget();

		deselect(new int[] { index });
	}

	/**
	 * Deselects the items at the given zero-relative indices in the receiver. If
	 * the item at the given zero-relative index in the receiver is selected, it is
	 * deselected. If the item at the index was not selected, it remains deselected.
	 * The range of the indices is inclusive. Indices that are out of range are
	 * ignored.
	 *
	 * @param start the start index of the items to deselect
	 * @param end   the end index of the items to deselect
	 *
	 * @exception SWTException
	 *                         <ul>
	 *                         <li>ERROR_WIDGET_DISPOSED - if the receiver has been
	 *                         disposed</li>
	 *                         <li>ERROR_THREAD_INVALID_ACCESS - if not called from
	 *                         the thread that created the receiver</li>
	 *                         </ul>
	 */
	public void deselect(int start, int end) {
		checkWidget();

		if (start > end) return;

		if (start < 0) {
			start = 0;
		}
		if (end >= itemsList.size()) {
			end = itemsList.size() - 1;
		}

		int[] arr = new int[end - start + 1];
		for (int s = start; s <= end; s++) {
			arr[s - start] = s;
		}

		deselect(arr);
	}

	/**
	 * Deselects all selected items in the receiver.
	 *
	 * @exception SWTException
	 *                         <ul>
	 *                         <li>ERROR_WIDGET_DISPOSED - if the receiver has been
	 *                         disposed</li>
	 *                         <li>ERROR_THREAD_INVALID_ACCESS - if not called from
	 *                         the thread that created the receiver</li>
	 *                         </ul>
	 */
	public void deselectAll() {
		checkWidget();

		selectionModel.clearSelection();
		redraw();
	}

	void destroyItem(TableItem item) {
		final int index = indexOf(item);
		if (index < 0) {
			return;
		}

		selectionModel.remove(index);

		if (!isVirtual()) {
			itemsList.remove(item);
		}
		// for virtual items, we have to take care, that these are not in
		// virtualItemsList
	}

	/**
	 * Returns the column at the given, zero-relative index in the receiver. Throws
	 * an exception if the index is out of range. Columns are returned in the order
	 * that they were created. If no <code>TableColumn</code>s were created by the
	 * programmer, this method will throw <code>ERROR_INVALID_RANGE</code> despite
	 * the fact that a single column of data may be visible in the table. This
	 * occurs when the programmer uses the table like a list, adding items but never
	 * creating a column.
	 *
	 * @param index the index of the column to return
	 * @return the column at the given index
	 *
	 * @exception IllegalArgumentException
	 *                                     <ul>
	 *                                     <li>ERROR_INVALID_RANGE - if the index is
	 *                                     not between 0 and the number of elements
	 *                                     in the list minus 1 (inclusive)</li>
	 *                                     </ul>
	 * @exception SWTException
	 *                                     <ul>
	 *                                     <li>ERROR_WIDGET_DISPOSED - if the
	 *                                     receiver has been disposed</li>
	 *                                     <li>ERROR_THREAD_INVALID_ACCESS - if not
	 *                                     called from the thread that created the
	 *                                     receiver</li>
	 *                                     </ul>
	 *
	 * @see Table#getColumnOrder()
	 * @see Table#setColumnOrder(int[])
	 * @see TableColumn#getMoveable()
	 * @see TableColumn#setMoveable(boolean)
	 * @see SWT#Move
	 */
	public TableColumn getColumn(int index) {
		checkWidget();
		if (0 > index || index >= columnsList.size()) error(SWT.ERROR_INVALID_RANGE);
		return columnsList.get(index);
	}

	/**
	 * Returns the number of columns contained in the receiver. If no
	 * <code>TableColumn</code>s were created by the programmer, this value is zero,
	 * despite the fact that visually, one column of items may be visible. This
	 * occurs when the programmer uses the table like a list, adding items but never
	 * creating a column.
	 *
	 * @return the number of columns
	 *
	 * @exception SWTException
	 *                         <ul>
	 *                         <li>ERROR_WIDGET_DISPOSED - if the receiver has been
	 *                         disposed</li>
	 *                         <li>ERROR_THREAD_INVALID_ACCESS - if not called from
	 *                         the thread that created the receiver</li>
	 *                         </ul>
	 */
	public int getColumnCount() {
		checkWidget();
		return columnsList.size();
	}

	/**
	 * Returns an array of zero-relative integers that map the creation order of the
	 * receiver's items to the order in which they are currently being displayed.
	 * <p>
	 * Specifically, the indices of the returned array represent the current visual
	 * order of the items, and the contents of the array represent the creation
	 * order of the items.
	 * </p>
	 * <p>
	 * Note: This is not the actual structure used by the receiver to maintain its
	 * list of items, so modifying the array will not affect the receiver.
	 * </p>
	 *
	 * @return the current visual order of the receiver's items
	 *
	 * @exception SWTException
	 *                         <ul>
	 *                         <li>ERROR_WIDGET_DISPOSED - if the receiver has been
	 *                         disposed</li>
	 *                         <li>ERROR_THREAD_INVALID_ACCESS - if not called from
	 *                         the thread that created the receiver</li>
	 *                         </ul>
	 *
	 * @see Table#setColumnOrder(int[])
	 * @see TableColumn#getMoveable()
	 * @see TableColumn#setMoveable(boolean)
	 * @see SWT#Move
	 *
	 * @since 3.1
	 */
	public int[] getColumnOrder() {
		checkWidget();

		if (columnsList.isEmpty()) {
			return new int[0];
		}

		if (columnOrder == null) {
			this.columnOrder = new int[columnsList.size()];

			for (int i = 0; i < columnsList.size(); i++) {
				columnOrder[i] = i;
			}
		}

		return columnOrder;
	}

	/**
	 * Returns an array of <code>TableColumn</code>s which are the columns in the
	 * receiver. Columns are returned in the order that they were created. If no
	 * <code>TableColumn</code>s were created by the programmer, the array is empty,
	 * despite the fact that visually, one column of items may be visible. This
	 * occurs when the programmer uses the table like a list, adding items but never
	 * creating a column.
	 * <p>
	 * Note: This is not the actual structure used by the receiver to maintain its
	 * list of items, so modifying the array will not affect the receiver.
	 * </p>
	 *
	 * @return the items in the receiver
	 *
	 * @exception SWTException
	 *                         <ul>
	 *                         <li>ERROR_WIDGET_DISPOSED - if the receiver has been
	 *                         disposed</li>
	 *                         <li>ERROR_THREAD_INVALID_ACCESS - if not called from
	 *                         the thread that created the receiver</li>
	 *                         </ul>
	 *
	 * @see Table#getColumnOrder()
	 * @see Table#setColumnOrder(int[])
	 * @see TableColumn#getMoveable()
	 * @see TableColumn#setMoveable(boolean)
	 * @see SWT#Move
	 */
	public TableColumn[] getColumns() {
		checkWidget();

		return columnsList.toArray(new TableColumn[0]);
	}

	int getFocusIndex() {
		// checkWidget ();

		logNotImplemented();
		return 0;
	}

	/**
	 * Returns the width in points of a grid line.
	 *
	 * @return the width of a grid line in points
	 *
	 * @exception SWTException
	 *                         <ul>
	 *                         <li>ERROR_WIDGET_DISPOSED - if the receiver has been
	 *                         disposed</li>
	 *                         <li>ERROR_THREAD_INVALID_ACCESS - if not called from
	 *                         the thread that created the receiver</li>
	 *                         </ul>
	 */
	public int getGridLineWidth() {
		checkWidget();
		return DPIUtil.scaleDown(getGridLineWidthInPixels(), 100);
	}

	int getGridLineWidthInPixels() {
		return GRID_WIDTH;
	}

	/**
	 * Returns the header background color.
	 *
	 * @return the receiver's header background color.
	 *
	 * @exception SWTException
	 *                         <ul>
	 *                         <li>ERROR_WIDGET_DISPOSED - if the receiver has been
	 *                         disposed</li>
	 *                         <li>ERROR_THREAD_INVALID_ACCESS - if not called from
	 *                         the thread that created the receiver</li>
	 *                         </ul>
	 * @since 3.106
	 */
	public Color getHeaderBackground() {
		checkWidget();

		if (headerBackgroundColor != null) {
			return headerBackgroundColor;
		}

		return Display.getDefault().getSystemColor(SWT.COLOR_WHITE);
	}

	/**
	 * Returns the header foreground color.
	 *
	 * @return the receiver's header foreground color.
	 *
	 * @exception SWTException
	 *                         <ul>
	 *                         <li>ERROR_WIDGET_DISPOSED - if the receiver has been
	 *                         disposed</li>
	 *                         <li>ERROR_THREAD_INVALID_ACCESS - if not called from
	 *                         the thread that created the receiver</li>
	 *                         </ul>
	 * @since 3.106
	 */
	public Color getHeaderForeground() {
		checkWidget();

		if (headerForegroundColor != null) {
			return headerForegroundColor;
		}

		return Display.getDefault().getSystemColor(SWT.COLOR_BLACK);
	}

	/**
	 * Returns the height of the receiver's header
	 *
	 * @return the height of the header or zero if the header is not visible
	 *
	 * @exception SWTException
	 *                         <ul>
	 *                         <li>ERROR_WIDGET_DISPOSED - if the receiver has been
	 *                         disposed</li>
	 *                         <li>ERROR_THREAD_INVALID_ACCESS - if not called from
	 *                         the thread that created the receiver</li>
	 *                         </ul>
	 *
	 * @since 2.0
	 */
	public int getHeaderHeight() {
		checkWidget();

		return headerVisible ? columnsHandler.getSize().y : 0;
	}

	/**
	 * Returns <code>true</code> if the receiver's header is visible, and
	 * <code>false</code> otherwise.
	 * <p>
	 * If one of the receiver's ancestors is not visible or some other condition
	 * makes the receiver not visible, this method may still indicate that it is
	 * considered visible even though it may not actually be showing.
	 * </p>
	 *
	 * @return the receiver's header's visibility state
	 *
	 * @exception SWTException
	 *                         <ul>
	 *                         <li>ERROR_WIDGET_DISPOSED - if the receiver has been
	 *                         disposed</li>
	 *                         <li>ERROR_THREAD_INVALID_ACCESS - if not called from
	 *                         the thread that created the receiver</li>
	 *                         </ul>
	 */
	public boolean getHeaderVisible() {
		checkWidget();

		return headerVisible;
	}

	/**
	 * Returns the item at the given, zero-relative index in the receiver. Throws an
	 * exception if the index is out of range.
	 *
	 * @param index the index of the item to return
	 * @return the item at the given index
	 *
	 * @exception IllegalArgumentException
	 *                                     <ul>
	 *                                     <li>ERROR_INVALID_RANGE - if the index is
	 *                                     not between 0 and the number of elements
	 *                                     in the list minus 1 (inclusive)</li>
	 *                                     </ul>
	 * @exception SWTException
	 *                                     <ul>
	 *                                     <li>ERROR_WIDGET_DISPOSED - if the
	 *                                     receiver has been disposed</li>
	 *                                     <li>ERROR_THREAD_INVALID_ACCESS - if not
	 *                                     called from the thread that created the
	 *                                     receiver</li>
	 *                                     </ul>
	 */
	public TableItem getItem(int index) {
		checkWidget();
		return _getItem(index);
	}

	/**
	 * Returns the item at the given point in the receiver or null if no such item
	 * exists. The point is in the coordinate system of the receiver.
	 * <p>
	 * The item that is returned represents an item that could be selected by the
	 * user. For example, if selection only occurs in items in the first column,
	 * then null is returned if the point is outside of the item. Note that the
	 * SWT.FULL_SELECTION style hint, which specifies the selection policy,
	 * determines the extent of the selection.
	 * </p>
	 *
	 * @param point the point used to locate the item
	 * @return the item at the given point, or null if the point is not in a
	 *         selectable item
	 *
	 * @exception IllegalArgumentException
	 *                                     <ul>
	 *                                     <li>ERROR_NULL_ARGUMENT - if the point is
	 *                                     null</li>
	 *                                     </ul>
	 * @exception SWTException
	 *                                     <ul>
	 *                                     <li>ERROR_WIDGET_DISPOSED - if the
	 *                                     receiver has been disposed</li>
	 *                                     <li>ERROR_THREAD_INVALID_ACCESS - if not
	 *                                     called from the thread that created the
	 *                                     receiver</li>
	 *                                     </ul>
	 */
	public TableItem getItem(Point point) {
		checkWidget();
		if (point == null) error(SWT.ERROR_NULL_ARGUMENT);
		final int max = Math.min(getItemCount(), itemsHandler.getLastVisibleElementIndex() + 1);
		for (int i = getTopIndex(); i < max; i++) {
			TableItem it = getItem(i);
			if (it != null && it.getBounds().contains(point)) {
				return it;
			}
		}
		return null;
	}

	/**
	 * Returns the number of items contained in the receiver.
	 *
	 * @return the number of items
	 *
	 * @exception SWTException
	 *                         <ul>
	 *                         <li>ERROR_WIDGET_DISPOSED - if the receiver has been
	 *                         disposed</li>
	 *                         <li>ERROR_THREAD_INVALID_ACCESS - if not called from
	 *                         the thread that created the receiver</li>
	 *                         </ul>
	 */
	public int getItemCount() {
		checkWidget();

		if (isVirtual()) {
			return virtualItemCount;
		}

		return itemsList.size();
	}

	boolean isVirtual() {
		return (getStyle() & SWT.VIRTUAL) != 0;
	}

	/**
	 * Returns the height of the area which would be used to display <em>one</em> of
	 * the items in the receiver.
	 *
	 * @return the height of one item
	 *
	 * @exception SWTException
	 *                         <ul>
	 *                         <li>ERROR_WIDGET_DISPOSED - if the receiver has been
	 *                         disposed</li>
	 *                         <li>ERROR_THREAD_INVALID_ACCESS - if not called from
	 *                         the thread that created the receiver</li>
	 *                         </ul>
	 */
	public int getItemHeight() {
		checkWidget();

		final int height = itemsList.isEmpty()
				? TableItemRenderer.guessItemHeight(this)
				: itemsList.get(selectionModel.getTopIndex()).getBounds().height;
		return Math.max(1, height);
	}

	/**
	 * Returns a (possibly empty) array of <code>TableItem</code>s which are the
	 * items in the receiver.
	 * <p>
	 * Note: This is not the actual structure used by the receiver to maintain its
	 * list of items, so modifying the array will not affect the receiver.
	 * </p>
	 *
	 * @return the items in the receiver
	 *
	 * @exception SWTException
	 *                         <ul>
	 *                         <li>ERROR_WIDGET_DISPOSED - if the receiver has been
	 *                         disposed</li>
	 *                         <li>ERROR_THREAD_INVALID_ACCESS - if not called from
	 *                         the thread that created the receiver</li>
	 *                         </ul>
	 */
	public TableItem[] getItems() {
		checkWidget();

		if (isVirtual()) {
			TableItem[] result = new TableItem[getItemCount()];
			for (int i = 0; i < getItemCount(); i++) {
				result[i] = _getItem(i);
			}
			return result;
		}

		return itemsList.toArray(new TableItem[0]);
	}

	/**
	 * Returns <code>true</code> if the receiver's lines are visible, and
	 * <code>false</code> otherwise. Note that some platforms draw grid lines while
	 * others may draw alternating row colors.
	 * <p>
	 * If one of the receiver's ancestors is not visible or some other condition
	 * makes the receiver not visible, this method may still indicate that it is
	 * considered visible even though it may not actually be showing.
	 * </p>
	 *
	 * @return the visibility state of the lines
	 *
	 * @exception SWTException
	 *                         <ul>
	 *                         <li>ERROR_WIDGET_DISPOSED - if the receiver has been
	 *                         disposed</li>
	 *                         <li>ERROR_THREAD_INVALID_ACCESS - if not called from
	 *                         the thread that created the receiver</li>
	 *                         </ul>
	 */
	public boolean getLinesVisible() {
		checkWidget();
		return linesVisible;
	}

	/**
	 * Returns an array of <code>TableItem</code>s that are currently selected in
	 * the receiver. The order of the items is unspecified. An empty array indicates
	 * that no items are selected.
	 * <p>
	 * Note: This is not the actual structure used by the receiver to maintain its
	 * selection, so modifying the array will not affect the receiver.
	 * </p>
	 *
	 * @return an array representing the selection
	 *
	 * @exception SWTException
	 *                         <ul>
	 *                         <li>ERROR_WIDGET_DISPOSED - if the receiver has been
	 *                         disposed</li>
	 *                         <li>ERROR_THREAD_INVALID_ACCESS - if not called from
	 *                         the thread that created the receiver</li>
	 *                         </ul>
	 */
	public TableItem[] getSelection() {
		checkWidget();
		final int[] selectionIndices = selectionModel.getSelectionIndices();
		final TableItem[] tableItems = new TableItem[selectionIndices.length];
		for (int i = 0; i < selectionIndices.length; i++) {
			final int index = selectionIndices[i];
			tableItems[i] = _getItem(index);
		}
		return tableItems;
	}

	/**
	 * Returns the number of selected items contained in the receiver.
	 *
	 * @return the number of selected items
	 *
	 * @exception SWTException
	 *                         <ul>
	 *                         <li>ERROR_WIDGET_DISPOSED - if the receiver has been
	 *                         disposed</li>
	 *                         <li>ERROR_THREAD_INVALID_ACCESS - if not called from
	 *                         the thread that created the receiver</li>
	 *                         </ul>
	 */
	public int getSelectionCount() {
		checkWidget();
		return selectionModel.selectionCount();
	}

	/**
	 * Returns the zero-relative index of the item which is currently selected in
	 * the receiver, or -1 if no item is selected.
	 *
	 * @return the index of the selected item
	 *
	 * @exception SWTException
	 *                         <ul>
	 *                         <li>ERROR_WIDGET_DISPOSED - if the receiver has been
	 *                         disposed</li>
	 *                         <li>ERROR_THREAD_INVALID_ACCESS - if not called from
	 *                         the thread that created the receiver</li>
	 *                         </ul>
	 */
	public int getSelectionIndex() {
		checkWidget();

		return selectionModel.getSelectionIndex();
	}

	/**
	 * @return rectangle which contains all visible columns of the table
	 */
	Rectangle getHeaderBounds() {
		return columnsHandler.getHeaderBounds();
	}

	/**
	 * @return rectangle which contains all visible items of the table
	 */
	Rectangle getItemsArea() {
		return itemsHandler.getItemsClientArea();
	}

	/**
	 * Returns the zero-relative indices of the items which are currently selected
	 * in the receiver. The order of the indices is unspecified. The array is empty
	 * if no items are selected.
	 * <p>
	 * Note: This is not the actual structure used by the receiver to maintain its
	 * selection, so modifying the array will not affect the receiver.
	 * </p>
	 *
	 * @return the array of indices of the selected items
	 *
	 * @exception SWTException
	 *                         <ul>
	 *                         <li>ERROR_WIDGET_DISPOSED - if the receiver has been
	 *                         disposed</li>
	 *                         <li>ERROR_THREAD_INVALID_ACCESS - if not called from
	 *                         the thread that created the receiver</li>
	 *                         </ul>
	 */
	public int[] getSelectionIndices() {
		checkWidget();
		return selectionModel.getSelectionIndices();
	}

	/**
	 * Returns the column which shows the sort indicator for the receiver. The value
	 * may be null if no column shows the sort indicator.
	 *
	 * @return the sort indicator
	 *
	 * @exception SWTException
	 *                         <ul>
	 *                         <li>ERROR_WIDGET_DISPOSED - if the receiver has been
	 *                         disposed</li>
	 *                         <li>ERROR_THREAD_INVALID_ACCESS - if not called from
	 *                         the thread that created the receiver</li>
	 *                         </ul>
	 *
	 * @see #setSortColumn(TableColumn)
	 *
	 * @since 3.2
	 */
	public TableColumn getSortColumn() {
		checkWidget();
		return sortColumn;
	}

	int getSortColumnPixel() {
		logNotImplemented();
		return 0;
	}

	/**
	 * Returns the direction of the sort indicator for the receiver. The value will
	 * be one of <code>UP</code>, <code>DOWN</code> or <code>NONE</code>.
	 *
	 * @return the sort direction
	 *
	 * @exception SWTException
	 *                         <ul>
	 *                         <li>ERROR_WIDGET_DISPOSED - if the receiver has been
	 *                         disposed</li>
	 *                         <li>ERROR_THREAD_INVALID_ACCESS - if not called from
	 *                         the thread that created the receiver</li>
	 *                         </ul>
	 *
	 * @see #setSortDirection(int)
	 *
	 * @since 3.2
	 */
	public int getSortDirection() {
		checkWidget();
		return sortDirection;
	}

	/**
	 * Returns the zero-relative index of the item which is currently at the top of
	 * the receiver. This index can change when items are scrolled or new items are
	 * added or removed.
	 *
	 * @return the index of the top item
	 *
	 * @exception SWTException
	 *                         <ul>
	 *                         <li>ERROR_WIDGET_DISPOSED - if the receiver has been
	 *                         disposed</li>
	 *                         <li>ERROR_THREAD_INVALID_ACCESS - if not called from
	 *                         the thread that created the receiver</li>
	 *                         </ul>
	 */
	public int getTopIndex() {
		checkWidget();
		return selectionModel.getTopIndex();
	}

	private boolean hasChildren() {
		logNotImplemented();
		return false;
	}

	boolean hitTestSelection(int index, int x, int y) {
		logNotImplemented();
		return false;
	}

	/**
	 * Searches the receiver's list starting at the first column (index 0) until a
	 * column is found that is equal to the argument, and returns the index of that
	 * column. If no column is found, returns -1.
	 *
	 * @param column the search column
	 * @return the index of the column
	 *
	 * @exception IllegalArgumentException
	 *                                     <ul>
	 *                                     <li>ERROR_NULL_ARGUMENT - if the column
	 *                                     is null</li>
	 *                                     </ul>
	 * @exception SWTException
	 *                                     <ul>
	 *                                     <li>ERROR_WIDGET_DISPOSED - if the
	 *                                     receiver has been disposed</li>
	 *                                     <li>ERROR_THREAD_INVALID_ACCESS - if not
	 *                                     called from the thread that created the
	 *                                     receiver</li>
	 *                                     </ul>
	 */
	public int indexOf(TableColumn column) {
		checkWidget();
		if (column == null) error(SWT.ERROR_NULL_ARGUMENT);
		return columnsList.indexOf(column);
	}

	/**
	 * Searches the receiver's list starting at the first item (index 0) until an
	 * item is found that is equal to the argument, and returns the index of that
	 * item. If no item is found, returns -1.
	 *
	 * @param item the search item
	 * @return the index of the item
	 *
	 * @exception IllegalArgumentException
	 *                                     <ul>
	 *                                     <li>ERROR_NULL_ARGUMENT - if the item is
	 *                                     null</li>
	 *                                     </ul>
	 * @exception SWTException
	 *                                     <ul>
	 *                                     <li>ERROR_WIDGET_DISPOSED - if the
	 *                                     receiver has been disposed</li>
	 *                                     <li>ERROR_THREAD_INVALID_ACCESS - if not
	 *                                     called from the thread that created the
	 *                                     receiver</li>
	 *                                     </ul>
	 */
	public int indexOf(TableItem item) {
		checkWidget();
		if (item == null) error(SWT.ERROR_NULL_ARGUMENT);

		if (isVirtual()) {
			for (Map.Entry<Integer, TableItem> e : virtualItemsList.entrySet()) {
				if (item.equals(e.getValue())) {
					return e.getKey();
				}
			}
			return -1;
		}
		return itemsList.indexOf(item);
	}

	public int[] indicesOf(TableItem[] items) {
		checkWidget();

		if (items == null) return null;

		int[] indexes = new int[items.length];
		for (int currentIndex = 0; currentIndex < items.length; currentIndex++) {
			indexes[currentIndex] = indexOf(items[currentIndex]);
		}
		return indexes;
	}

	boolean isCustomToolTip() {
		return hooks(SWT.MeasureItem);
	}

	/**
	 * Returns <code>true</code> if the item is selected, and <code>false</code>
	 * otherwise. Indices out of range are ignored.
	 *
	 * @param index the index of the item
	 * @return the selection state of the item at the index
	 *
	 * @exception SWTException
	 *                         <ul>
	 *                         <li>ERROR_WIDGET_DISPOSED - if the receiver has been
	 *                         disposed</li>
	 *                         <li>ERROR_THREAD_INVALID_ACCESS - if not called from
	 *                         the thread that created the receiver</li>
	 *                         </ul>
	 */
	public boolean isSelected(int index) {
		return selectionModel.isSelected(index);
	}

	/**
	 * Removes the items from the receiver's list at the given zero-relative
	 * indices.
	 *
	 * @param indices the array of indices of the items
	 *
	 * @exception IllegalArgumentException
	 *                                     <ul>
	 *                                     <li>ERROR_INVALID_RANGE - if the index is
	 *                                     not between 0 and the number of elements
	 *                                     in the list minus 1 (inclusive)</li>
	 *                                     <li>ERROR_NULL_ARGUMENT - if the indices
	 *                                     array is null</li>
	 *                                     </ul>
	 * @exception SWTException
	 *                                     <ul>
	 *                                     <li>ERROR_WIDGET_DISPOSED - if the
	 *                                     receiver has been disposed</li>
	 *                                     <li>ERROR_THREAD_INVALID_ACCESS - if not
	 *                                     called from the thread that created the
	 *                                     receiver</li>
	 *                                     </ul>
	 */
	public void remove(int[] indices) {
		checkWidget();

		if (indices == null) error(SWT.ERROR_NULL_ARGUMENT);

		Set<Integer> set = new HashSet<>();
		for (int index : indices) {
			set.add(index);
		}
		List<Integer> indicesList = new ArrayList<>(set);
		Collections.sort(indicesList);
		for (int i = indicesList.size() - 1; i >= 0; i--) {
			int index = indicesList.get(i);
			if (index >= 0 && index < getItemCount()) {
				TableItem item = _getItem(index, false);
				if (item != null) {
					item.dispose();
				}
			} else {
				error(SWT.ERROR_INVALID_RANGE);
			}
		}
	}

	/**
	 * Removes the item from the receiver at the given zero-relative index.
	 *
	 * @param index the index for the item
	 *
	 * @exception IllegalArgumentException
	 *                                     <ul>
	 *                                     <li>ERROR_INVALID_RANGE - if the index is
	 *                                     not between 0 and the number of elements
	 *                                     in the list minus 1 (inclusive)</li>
	 *                                     </ul>
	 * @exception SWTException
	 *                                     <ul>
	 *                                     <li>ERROR_WIDGET_DISPOSED - if the
	 *                                     receiver has been disposed</li>
	 *                                     <li>ERROR_THREAD_INVALID_ACCESS - if not
	 *                                     called from the thread that created the
	 *                                     receiver</li>
	 *                                     </ul>
	 */
	public void remove(int index) {
		checkWidget();
	}

	/**
	 * Removes the items from the receiver which are between the given zero-relative
	 * start and end indices (inclusive).
	 *
	 * @param start the start of the range
	 * @param end   the end of the range
	 *
	 * @exception IllegalArgumentException
	 *                                     <ul>
	 *                                     <li>ERROR_INVALID_RANGE - if either the
	 *                                     start or end are not between 0 and the
	 *                                     number of elements in the list minus 1
	 *                                     (inclusive)</li>
	 *                                     </ul>
	 * @exception SWTException
	 *                                     <ul>
	 *                                     <li>ERROR_WIDGET_DISPOSED - if the
	 *                                     receiver has been disposed</li>
	 *                                     <li>ERROR_THREAD_INVALID_ACCESS - if not
	 *                                     called from the thread that created the
	 *                                     receiver</li>
	 *                                     </ul>
	 */
	public void remove(int start, int end) {
		checkWidget();

		if (start > end) return;
		if (start < 0 || end >= getItemCount()) error(SWT.ERROR_INVALID_RANGE);

		int[] indices = new int[end - start + 1];
		for (int i = start; i <= end; i++) {
			indices[i - start] = i;
		}
		remove(indices);
	}

	/**
	 * Removes all of the items from the receiver.
	 *
	 * @exception SWTException
	 *                         <ul>
	 *                         <li>ERROR_WIDGET_DISPOSED - if the receiver has been
	 *                         disposed</li>
	 *                         <li>ERROR_THREAD_INVALID_ACCESS - if not called from
	 *                         the thread that created the receiver</li>
	 *                         </ul>
	 */
	public void removeAll() {
		if (isVirtual()) {
			Set<TableItem> s = new HashSet<>(virtualItemsList.values());
			virtualItemsList.clear();
			virtualItemCount = 0;
			s.forEach(Widget::dispose);
			return;
		}

		remove(0, getItemCount() - 1);
	}

	/**
	 * Removes the listener from the collection of listeners who will be notified
	 * when the user changes the receiver's selection.
	 *
	 * @param listener the listener which should no longer be notified
	 *
	 * @exception IllegalArgumentException
	 *                                     <ul>
	 *                                     <li>ERROR_NULL_ARGUMENT - if the listener
	 *                                     is null</li>
	 *                                     </ul>
	 * @exception SWTException
	 *                                     <ul>
	 *                                     <li>ERROR_WIDGET_DISPOSED - if the
	 *                                     receiver has been disposed</li>
	 *                                     <li>ERROR_THREAD_INVALID_ACCESS - if not
	 *                                     called from the thread that created the
	 *                                     receiver</li>
	 *                                     </ul>
	 *
	 * @see SelectionListener
	 * @see #addSelectionListener(SelectionListener)
	 */
	public void removeSelectionListener(SelectionListener listener) {
		checkWidget();
		if (listener == null) error(SWT.ERROR_NULL_ARGUMENT);
		if (eventTable == null) return;
		eventTable.unhook(SWT.Selection, listener);
		eventTable.unhook(SWT.DefaultSelection, listener);
	}

	/**
	 * Selects the items at the given zero-relative indices in the receiver. The
	 * current selection is not cleared before the new items are selected.
	 * <p>
	 * If the item at a given index is not selected, it is selected. If the item at
	 * a given index was already selected, it remains selected. Indices that are out
	 * of range and duplicate indices are ignored. If the receiver is single-select
	 * and multiple indices are specified, then all indices are ignored.
	 * </p>
	 *
	 * @param indices the array of indices for the items to select
	 *
	 * @exception IllegalArgumentException
	 *                                     <ul>
	 *                                     <li>ERROR_NULL_ARGUMENT - if the array of
	 *                                     indices is null</li>
	 *                                     </ul>
	 * @exception SWTException
	 *                                     <ul>
	 *                                     <li>ERROR_WIDGET_DISPOSED - if the
	 *                                     receiver has been disposed</li>
	 *                                     <li>ERROR_THREAD_INVALID_ACCESS - if not
	 *                                     called from the thread that created the
	 *                                     receiver</li>
	 *                                     </ul>
	 *
	 * @see Table#setSelection(int[])
	 */
	public void select(int[] indices) {
		checkWidget();
		if (indices == null) error(SWT.ERROR_NULL_ARGUMENT);

		if (selectionModel.select(indices)) {
			redraw();
		}
	}

	boolean hasItems() {
		if (isVirtual()) {
			return virtualItemCount > 0;
		}
		return !itemsList.isEmpty();
	}

	@Override
	void reskinChildren(int flags) {
		if (isVirtual()) {
			for (Map.Entry<Integer, TableItem> e : virtualItemsList.entrySet()) {
				TableItem it = e.getValue();
				if (it != null) {
					it.reskin(flags);
				}
			}
		} else {
			if (hasItems()) {
				int itemCount = getItemCount();
				for (int i = 0; i < itemCount; i++) {
					TableItem item = _getItem(i, false);
					if (item != null) {
						item.reskin(flags);
					}
				}
			}

		}
		if (columnsExist()) {
			for (int i = 0; i < getColumnCount(); i++) {
				TableColumn column = getColumn(i);
				if (!column.isDisposed())
					column.reskin(flags);
			}
		}
		super.reskinChildren(flags);
	}

	/**
	 * Selects the item at the given zero-relative index in the receiver. If the
	 * item at the index was already selected, it remains selected. Indices that are
	 * out of range are ignored.
	 *
	 * @param index the index of the item to select
	 *
	 * @exception SWTException
	 *                         <ul>
	 *                         <li>ERROR_WIDGET_DISPOSED - if the receiver has been
	 *                         disposed</li>
	 *                         <li>ERROR_THREAD_INVALID_ACCESS - if not called from
	 *                         the thread that created the receiver</li>
	 *                         </ul>
	 */
	public void select(int index) {
		checkWidget();
		if (index < 0 || index >= getItemCount()) return;

		if (selectionModel.select(new int[] {index})) {
			redraw();
		}
	}

	/**
	 * Selects the items in the range specified by the given zero-relative indices
	 * in the receiver. The range of indices is inclusive. The current selection is
	 * not cleared before the new items are selected.
	 * <p>
	 * If an item in the given range is not selected, it is selected. If an item in
	 * the given range was already selected, it remains selected. Indices that are
	 * out of range are ignored and no items will be selected if start is greater
	 * than end. If the receiver is single-select and there is more than one item in
	 * the given range, then all indices are ignored.
	 * </p>
	 *
	 * @param start the start of the range
	 * @param end   the end of the range
	 *
	 * @exception SWTException
	 *                         <ul>
	 *                         <li>ERROR_WIDGET_DISPOSED - if the receiver has been
	 *                         disposed</li>
	 *                         <li>ERROR_THREAD_INVALID_ACCESS - if not called from
	 *                         the thread that created the receiver</li>
	 *                         </ul>
	 *
	 * @see Table#setSelection(int,int)
	 */
	public void select(int start, int end) {
		checkWidget();

		if (itemsList.isEmpty()) return;
		if ((SWT.SINGLE & style) != 0 && start != end) return;
		if (end < start) return;
		if (end < 0) return;

		if (start < 0) {
			start = 0;
		}

		if (start > itemsList.size() - 1) return;

		if (end > itemsList.size() - 1) {
			end = itemsList.size() - 1;
		}

		int[] indices = new int[end - start + 1];
		for (int i = start; i <= end; i++) {
			indices[i - start] = i;
		}

		if (selectionModel.select(indices)) {
			redraw();
		}
	}

	/**
	 * Selects all of the items in the receiver.
	 * <p>
	 * If the receiver is single-select, do nothing.
	 * </p>
	 *
	 * @exception SWTException
	 *                         <ul>
	 *                         <li>ERROR_WIDGET_DISPOSED - if the receiver has been
	 *                         disposed</li>
	 *                         <li>ERROR_THREAD_INVALID_ACCESS - if not called from
	 *                         the thread that created the receiver</li>
	 *                         </ul>
	 */
	public void selectAll() {
		checkWidget();

		if ((style & SWT.SINGLE) != 0) return;
		if (itemsList.isEmpty()) return;

		select(0, itemsList.size() - 1);
	}

	void setBackgroundTransparent(boolean transparent) {
		logNotImplemented();
	}

	/**
	 * Sets the order that the items in the receiver should be displayed in to the
	 * given argument which is described in terms of the zero-relative ordering of
	 * when the items were added.
	 *
	 * @param order the new order to display the items
	 *
	 * @exception SWTException
	 *                                     <ul>
	 *                                     <li>ERROR_WIDGET_DISPOSED - if the
	 *                                     receiver has been disposed</li>
	 *                                     <li>ERROR_THREAD_INVALID_ACCESS - if not
	 *                                     called from the thread that created the
	 *                                     receiver</li>
	 *                                     </ul>
	 * @exception IllegalArgumentException
	 *                                     <ul>
	 *                                     <li>ERROR_NULL_ARGUMENT - if the item
	 *                                     order is null</li>
	 *                                     <li>ERROR_INVALID_ARGUMENT - if the item
	 *                                     order is not the same length as the
	 *                                     number of items</li>
	 *                                     </ul>
	 *
	 * @see Table#getColumnOrder()
	 * @see TableColumn#getMoveable()
	 * @see TableColumn#setMoveable(boolean)
	 * @see SWT#Move
	 *
	 * @since 3.1
	 */
	public void setColumnOrder(int[] order) {
		checkWidget();

		if (order == null) error(SWT.ERROR_NULL_ARGUMENT);
//		if (order.length == 0) {
//			this.columnOrder = null;
//			return;
//		}
		int columnCount = getColumnCount();
		if (columnCount == 0) {
			if (order.length != 0) error(SWT.ERROR_INVALID_ARGUMENT);
			return;
		}

		if (order.length != columnCount) {
			System.out.println("columnCount: " + columnCount + "  Input: " + Arrays.toString(order));
			error(SWT.ERROR_INVALID_ARGUMENT);
		}

		Set<Integer> set = new HashSet<>();
		Arrays.stream(order).forEach(e -> {
			if (e < 0 || e >= columnCount) {
				error(SWT.ERROR_INVALID_ARGUMENT);
			}

			set.add(e);
		});

		if (order.length != set.size()) error(SWT.ERROR_INVALID_ARGUMENT);

		this.columnOrder = order;
	}

	void setCustomDraw(boolean customDraw) {
		this.customDraw = customDraw;
	}

	void setDeferResize(boolean defer) {
		logNotImplemented();

	}

	void setCheckboxImageList(int width, int height, boolean fixScroll) {
		logNotImplemented();
	}

	boolean isFocusRow(int index) {
		return selectionModel.getCurrent() == index;
	}

	void setFocusIndex(int index) {
		if (index < 0 || index >= getItemCount()) {
			focusItem = null;
			return;
		}

		focusItem = getItem(index);

		logNotImplemented();
	}

	/**
	 * Sets the header background color to the color specified by the argument, or
	 * to the default system color if the argument is null.
	 * <p>
	 * Note: This operation is a <em>HINT</em> and is not supported on all
	 * platforms. If the native header has a 3D look and feel (e.g. Windows 7), this
	 * method will cause the header to look FLAT irrespective of the state of the
	 * table style.
	 * </p>
	 *
	 * @param color the new color (or null)
	 *
	 * @exception IllegalArgumentException
	 *                                     <ul>
	 *                                     <li>ERROR_INVALID_ARGUMENT - if the
	 *                                     argument has been disposed</li>
	 *                                     </ul>
	 * @exception SWTException
	 *                                     <ul>
	 *                                     <li>ERROR_WIDGET_DISPOSED - if the
	 *                                     receiver has been disposed</li>
	 *                                     <li>ERROR_THREAD_INVALID_ACCESS - if not
	 *                                     called from the thread that created the
	 *                                     receiver</li>
	 *                                     </ul>
	 * @since 3.106
	 */
	public void setHeaderBackground(Color color) {
		checkWidget();

		if (color != null && color.isDisposed()) error(SWT.ERROR_INVALID_ARGUMENT);

		this.headerBackgroundColor = color;
	}

	/**
	 * Sets the header foreground color to the color specified by the argument, or
	 * to the default system color if the argument is null.
	 * <p>
	 * Note: This operation is a <em>HINT</em> and is not supported on all
	 * platforms. If the native header has a 3D look and feel (e.g. Windows 7), this
	 * method will cause the header to look FLAT irrespective of the state of the
	 * table style.
	 * </p>
	 *
	 * @param color the new color (or null)
	 *
	 * @exception IllegalArgumentException
	 *                                     <ul>
	 *                                     <li>ERROR_INVALID_ARGUMENT - if the
	 *                                     argument has been disposed</li>
	 *                                     </ul>
	 * @exception SWTException
	 *                                     <ul>
	 *                                     <li>ERROR_WIDGET_DISPOSED - if the
	 *                                     receiver has been disposed</li>
	 *                                     <li>ERROR_THREAD_INVALID_ACCESS - if not
	 *                                     called from the thread that created the
	 *                                     receiver</li>
	 *                                     </ul>
	 * @since 3.106
	 */
	public void setHeaderForeground(Color color) {
		checkWidget();

		if (color != null && color.isDisposed()) error(SWT.ERROR_INVALID_ARGUMENT);

		this.headerForegroundColor = color;
	}

	/**
	 * Marks the receiver's header as visible if the argument is <code>true</code>,
	 * and marks it invisible otherwise.
	 * <p>
	 * If one of the receiver's ancestors is not visible or some other condition
	 * makes the receiver not visible, marking it visible may not actually cause it
	 * to be displayed.
	 * </p>
	 *
	 * @param show the new visibility state
	 *
	 * @exception SWTException
	 *                         <ul>
	 *                         <li>ERROR_WIDGET_DISPOSED - if the receiver has been
	 *                         disposed</li>
	 *                         <li>ERROR_THREAD_INVALID_ACCESS - if not called from
	 *                         the thread that created the receiver</li>
	 *                         </ul>
	 */
	public void setHeaderVisible(boolean show) {
		checkWidget();

		if (headerVisible == show) {
			return;
		}

		this.headerVisible = show;

		if (isVirtual()) {
			for (TableItem it : virtualItemsList.values()) {
				if (it != null) {
					it.clearCache();
				}
			}
		} else {
			for (TableItem it : itemsList) {
				it.clearCache();
			}
		}

		columnsHandler.clearCache();
		itemsHandler.clearCache();

		redraw();
	}

	/**
	 * Sets the number of items contained in the receiver.
	 *
	 * @param count the number of items
	 *
	 * @exception SWTException
	 *                         <ul>
	 *                         <li>ERROR_WIDGET_DISPOSED - if the receiver has been
	 *                         disposed</li>
	 *                         <li>ERROR_THREAD_INVALID_ACCESS - if not called from
	 *                         the thread that created the receiver</li>
	 *                         </ul>
	 *
	 * @since 3.0
	 */
	public void setItemCount(int count) {
		checkWidget();

		count = Math.max(0, count);
		if (isVirtual()) {
			if (count == virtualItemCount) {
				return;
			}

			boolean redraw = count > virtualItemCount;
			this.virtualItemCount = count;

			while (!virtualItemsList.isEmpty()) {
				int key = virtualItemsList.lastKey();
				if (key >= count) {
					virtualItemsList.remove(key);
				} else {
					break;
				}
			}

			selectionModel.setCount(count);

			if (redraw) {
				redraw();
			}

			updateVerticalScrollBar();
			return;
		}

		if (count == itemsList.size()) {
			return;
		}

		if (count > itemsList.size()) {
			for (int i = itemsList.size(); i < count; i++) {
				new TableItem(this, SWT.None);
			}
		} else {
			for (int i = itemsList.size() - 1; i >= count; i--) {
				itemsList.get(i).dispose();
			}
		}
	}

	void setItemHeight(boolean fixScroll) {
		int topIndex = getTopIndex();
		if (fixScroll && topIndex != 0) {
			setRedraw(false);
			setTopIndex(0);
		}

		logNotImplemented();
		if (fixScroll && topIndex != 0) {
			setTopIndex(topIndex);
			setRedraw(true);
		}
	}

	/**
	 * Sets the height of the area which would be used to display <em>one</em> of
	 * the items in the table.
	 *
	 * @param itemHeight the height of one item
	 *
	 * @exception SWTException
	 *                         <ul>
	 *                         <li>ERROR_WIDGET_DISPOSED - if the receiver has been
	 *                         disposed</li>
	 *                         <li>ERROR_THREAD_INVALID_ACCESS - if not called from
	 *                         the thread that created the receiver</li>
	 *                         </ul>
	 *
	 * @since 3.2
	 */
	/* public */ void setItemHeight(int itemHeight) {
		checkWidget();
		if (itemHeight < -1) error(SWT.ERROR_INVALID_ARGUMENT);

		this.itemHeight = itemHeight;
		logNotImplemented();
		setItemHeight(true);
		setScrollWidth(null, true);
	}

	/**
	 * Marks the receiver's lines as visible if the argument is <code>true</code>,
	 * and marks it invisible otherwise. Note that some platforms draw grid lines
	 * while others may draw alternating row colors.
	 * <p>
	 * If one of the receiver's ancestors is not visible or some other condition
	 * makes the receiver not visible, marking it visible may not actually cause it
	 * to be displayed.
	 * </p>
	 *
	 * @param show the new visibility state
	 *
	 * @exception SWTException
	 *                         <ul>
	 *                         <li>ERROR_WIDGET_DISPOSED - if the receiver has been
	 *                         disposed</li>
	 *                         <li>ERROR_THREAD_INVALID_ACCESS - if not called from
	 *                         the thread that created the receiver</li>
	 *                         </ul>
	 */
	public void setLinesVisible(boolean show) {
		checkWidget();
		this.linesVisible = show;
		logNotImplemented();
	}

	Point getTopIndexItemPosition() {
		Rectangle columns = getHeaderBounds();
		int gridLineSize = TableItemsHandler.getGridSize(this);
		int initialHeightPosition = headerVisible ? columns.height : 0;

		return new Point(columns.x, initialHeightPosition + gridLineSize);
	}

	@Override
	public void setRedraw(boolean redraw) {
		checkWidget();
		super.setRedraw(redraw);
	}

	void setScrollWidth(int width) {
		logNotImplemented();
	}

	boolean setScrollWidth(TableItem item, boolean force) {
		logNotImplemented();
		return false;
	}

	/**
	 * Selects the items at the given zero-relative indices in the receiver. The
	 * current selection is cleared before the new items are selected, and if
	 * necessary the receiver is scrolled to make the new selection visible.
	 * <p>
	 * Indices that are out of range and duplicate indices are ignored. If the
	 * receiver is single-select and multiple indices are specified, then all
	 * indices are ignored.
	 * </p>
	 *
	 * @param indices the indices of the items to select
	 *
	 * @exception IllegalArgumentException
	 *                                     <ul>
	 *                                     <li>ERROR_NULL_ARGUMENT - if the array of
	 *                                     indices is null</li>
	 *                                     </ul>
	 * @exception SWTException
	 *                                     <ul>
	 *                                     <li>ERROR_WIDGET_DISPOSED - if the
	 *                                     receiver has been disposed</li>
	 *                                     <li>ERROR_THREAD_INVALID_ACCESS - if not
	 *                                     called from the thread that created the
	 *                                     receiver</li>
	 *                                     </ul>
	 *
	 * @see Table#deselectAll()
	 * @see Table#select(int[])
	 */
	public void setSelection(int[] indices) {
		checkWidget();
		if (indices == null) error(SWT.ERROR_NULL_ARGUMENT);

		deselectAll();
		int length = indices.length;
		if (length == 0 || ((style & SWT.SINGLE) != 0 && length > 1)) return;

		Set<Integer> set = new TreeSet<>((o1, o2) -> o2 - o1);
		final int count = isVirtual() ? virtualItemCount : itemsList.size();
		for (int i : indices) {
			if (i >= 0 && i < count) {
				set.add(i);
			}
		}

		List<Integer> list = new ArrayList<>(set);

		int focusIndex = -1;
		for (int i = list.size() - 1; i >= 0; --i) {
			int index = list.get(i);
			if (index != -1) {
				select(focusIndex = index);
			}
		}
		if (focusIndex != -1) {
			setFocusIndex(focusIndex);
		}
		showSelection();
	}

	/**
	 * Sets the receiver's selection to the given item. The current selection is
	 * cleared before the new item is selected, and if necessary the receiver is
	 * scrolled to make the new selection visible.
	 * <p>
	 * If the item is not in the receiver, then it is ignored.
	 * </p>
	 *
	 * @param item the item to select
	 *
	 * @exception IllegalArgumentException
	 *                                     <ul>
	 *                                     <li>ERROR_NULL_ARGUMENT - if the item is
	 *                                     null</li>
	 *                                     <li>ERROR_INVALID_ARGUMENT - if the item
	 *                                     has been disposed</li>
	 *                                     </ul>
	 * @exception SWTException
	 *                                     <ul>
	 *                                     <li>ERROR_WIDGET_DISPOSED - if the
	 *                                     receiver has been disposed</li>
	 *                                     <li>ERROR_THREAD_INVALID_ACCESS - if not
	 *                                     called from the thread that created the
	 *                                     receiver</li>
	 *                                     </ul>
	 *
	 * @since 3.2
	 */
	public void setSelection(TableItem item) {
		checkWidget();

		if (item == null) error(SWT.ERROR_NULL_ARGUMENT);

		setSelection(new TableItem[] { item });
	}

	/**
	 * Sets the receiver's selection to be the given array of items. The current
	 * selection is cleared before the new items are selected, and if necessary the
	 * receiver is scrolled to make the new selection visible.
	 * <p>
	 * Items that are not in the receiver are ignored. If the receiver is
	 * single-select and multiple items are specified, then all items are ignored.
	 * </p>
	 *
	 * @param items the array of items
	 *
	 * @exception IllegalArgumentException
	 *                                     <ul>
	 *                                     <li>ERROR_NULL_ARGUMENT - if the array of
	 *                                     items is null</li>
	 *                                     <li>ERROR_INVALID_ARGUMENT - if one of
	 *                                     the items has been disposed</li>
	 *                                     </ul>
	 * @exception SWTException
	 *                                     <ul>
	 *                                     <li>ERROR_WIDGET_DISPOSED - if the
	 *                                     receiver has been disposed</li>
	 *                                     <li>ERROR_THREAD_INVALID_ACCESS - if not
	 *                                     called from the thread that created the
	 *                                     receiver</li>
	 *                                     </ul>
	 *
	 * @see Table#deselectAll()
	 * @see Table#select(int[])
	 * @see Table#setSelection(int[])
	 */
	public void setSelection(TableItem[] items) {
		checkWidget();
		if (items == null) error(SWT.ERROR_NULL_ARGUMENT);

		deselectAll();
		int length = items.length;
		if (length == 0 || ((style & SWT.SINGLE) != 0 && length > 1)) return;
		int focusIndex = -1;
		for (int i = length - 1; i >= 0; --i) {
			int index = indexOf(items[i]);
			if (index != -1) {
				select(focusIndex = index);
			}
		}
		if (focusIndex != -1) {
			setFocusIndex(focusIndex);
		}
		showSelection();
	}

	/**
	 * Selects the item at the given zero-relative index in the receiver. The
	 * current selection is first cleared, then the new item is selected, and if
	 * necessary the receiver is scrolled to make the new selection visible.
	 *
	 * @param index the index of the item to select
	 *
	 * @exception SWTException
	 *                         <ul>
	 *                         <li>ERROR_WIDGET_DISPOSED - if the receiver has been
	 *                         disposed</li>
	 *                         <li>ERROR_THREAD_INVALID_ACCESS - if not called from
	 *                         the thread that created the receiver</li>
	 *                         </ul>
	 *
	 * @see Table#deselectAll()
	 * @see Table#select(int)
	 */
	public void setSelection(int index) {
		checkWidget();
		deselectAll();
		select(index);
		if (index != -1) {
			setFocusIndex(index);
		}
		showSelection();
	}

	/**
	 * Selects the items in the range specified by the given zero-relative indices
	 * in the receiver. The range of indices is inclusive. The current selection is
	 * cleared before the new items are selected, and if necessary the receiver is
	 * scrolled to make the new selection visible.
	 * <p>
	 * Indices that are out of range are ignored and no items will be selected if
	 * start is greater than end. If the receiver is single-select and there is more
	 * than one item in the given range, then all indices are ignored.
	 * </p>
	 *
	 * @param start the start index of the items to select
	 * @param end   the end index of the items to select
	 *
	 * @exception SWTException
	 *                         <ul>
	 *                         <li>ERROR_WIDGET_DISPOSED - if the receiver has been
	 *                         disposed</li>
	 *                         <li>ERROR_THREAD_INVALID_ACCESS - if not called from
	 *                         the thread that created the receiver</li>
	 *                         </ul>
	 *
	 * @see Table#deselectAll()
	 * @see Table#select(int,int)
	 */
	public void setSelection(int start, int end) {
		checkWidget();

		deselectAll();
		if (end < 0 || start > end || ((style & SWT.SINGLE) != 0 && start != end)) return;
		int count = getItemCount();
		if (count == 0 || start >= count) return;
		start = Math.max(0, start);
		end = Math.min(end, count - 1);
		select(start, end);
		setFocusIndex(start);
		showSelection();
	}

	/**
	 * Sets the column used by the sort indicator for the receiver. A null value
	 * will clear the sort indicator. The current sort column is cleared before the
	 * new column is set.
	 *
	 * @param column the column used by the sort indicator or <code>null</code>
	 *
	 * @exception IllegalArgumentException
	 *                                     <ul>
	 *                                     <li>ERROR_INVALID_ARGUMENT - if the
	 *                                     column is disposed</li>
	 *                                     </ul>
	 * @exception SWTException
	 *                                     <ul>
	 *                                     <li>ERROR_WIDGET_DISPOSED - if the
	 *                                     receiver has been disposed</li>
	 *                                     <li>ERROR_THREAD_INVALID_ACCESS - if not
	 *                                     called from the thread that created the
	 *                                     receiver</li>
	 *                                     </ul>
	 *
	 * @since 3.2
	 */
	public void setSortColumn(TableColumn column) {
		checkWidget();

		logNotImplemented();
	}

	@Override
	public void pack() {
		super.pack();
		redraw();
	}

	/**
	 * Sets the direction of the sort indicator for the receiver. The value can be
	 * one of <code>UP</code>, <code>DOWN</code> or <code>NONE</code>.
	 *
	 * @param direction the direction of the sort indicator
	 *
	 * @exception SWTException
	 *                         <ul>
	 *                         <li>ERROR_WIDGET_DISPOSED - if the receiver has been
	 *                         disposed</li>
	 *                         <li>ERROR_THREAD_INVALID_ACCESS - if not called from
	 *                         the thread that created the receiver</li>
	 *                         </ul>
	 *
	 * @since 3.2
	 */
	public void setSortDirection(int direction) {
		checkWidget();
		logNotImplemented();
	}

	void setSubImagesVisible(boolean visible) {
		logNotImplemented();
	}

	void setTableEmpty() {
		logNotImplemented();
	}

	/**
	 * Sets the zero-relative index of the item which is currently at the top of the
	 * receiver. This index can change when items are scrolled or new items are
	 * added and removed.
	 *
	 * @param index the index of the top item
	 *
	 * @exception SWTException
	 *                         <ul>
	 *                         <li>ERROR_WIDGET_DISPOSED - if the receiver has been
	 *                         disposed</li>
	 *                         <li>ERROR_THREAD_INVALID_ACCESS - if not called from
	 *                         the thread that created the receiver</li>
	 *                         </ul>
	 */
	public void setTopIndex(int index) {
		checkWidget();

		if (index == selectionModel.getTopIndex()) return;

		final int itemCount = getItemCount();
		if (itemCount == 0) {
			selectionModel.setTopIndex(0);
			return;
		}

		if (index > itemCount) {
			index = itemCount - 1;
		}

		selectionModel.setTopIndex(index);

		if (mouseHoverElement instanceof TableItem) {
			mouseHoverElement = null;
		}

		if (verticalBar != null) {
			verticalBar.setSelection(index);
		}
		redraw();
	}

	/**
	 * Shows the column. If the column is already showing in the receiver, this
	 * method simply returns. Otherwise, the columns are scrolled until the column
	 * is visible.
	 *
	 * @param column the column to be shown
	 *
	 * @exception IllegalArgumentException
	 *                                     <ul>
	 *                                     <li>ERROR_NULL_ARGUMENT - if the column
	 *                                     is null</li>
	 *                                     <li>ERROR_INVALID_ARGUMENT - if the
	 *                                     column has been disposed</li>
	 *                                     </ul>
	 * @exception SWTException
	 *                                     <ul>
	 *                                     <li>ERROR_WIDGET_DISPOSED - if the
	 *                                     receiver has been disposed</li>
	 *                                     <li>ERROR_THREAD_INVALID_ACCESS - if not
	 *                                     called from the thread that created the
	 *                                     receiver</li>
	 *                                     </ul>
	 *
	 * @since 3.0
	 */
	public void showColumn(TableColumn column) {
		checkWidget();
		if (column == null) error(SWT.ERROR_NULL_ARGUMENT);
		if (column.isDisposed()) error(SWT.ERROR_INVALID_ARGUMENT);
		if (column.getParent() != this) error(SWT.ERROR_INVALID_ARGUMENT);
		if (!isVisible()) return;

		int index = indexOf(column);
		if (0 > index || index >= getColumnCount()) return;

		Rectangle ca = getClientArea();

		updateColumnsX();
		int horShift = column.getX();
		if (ca.x < horShift && ca.x + ca.width > horShift) return;

		final ScrollBar horizontalBar = getHorizontalBar();
		if (horizontalBar != null) {
			horizontalBar.setSelection(horizontalBar.getSelection() + horShift);
			redraw();
		}
	}

	void showItem(int index) {
		if (index < getTopIndex() || index > itemsHandler.getLastVisibleElementIndex()) {
			setTopIndex(index);
			redraw();
		}
	}

	int getLastVisibleIndex() {
		return itemsHandler.getLastVisibleElementIndex();
	}

	/**
	 * Shows the item. If the item is already showing in the receiver, this method
	 * simply returns. Otherwise, the items are scrolled until the item is visible.
	 *
	 * @param item the item to be shown
	 *
	 * @exception IllegalArgumentException
	 *                                     <ul>
	 *                                     <li>ERROR_NULL_ARGUMENT - if the item is
	 *                                     null</li>
	 *                                     <li>ERROR_INVALID_ARGUMENT - if the item
	 *                                     has been disposed</li>
	 *                                     </ul>
	 * @exception SWTException
	 *                                     <ul>
	 *                                     <li>ERROR_WIDGET_DISPOSED - if the
	 *                                     receiver has been disposed</li>
	 *                                     <li>ERROR_THREAD_INVALID_ACCESS - if not
	 *                                     called from the thread that created the
	 *                                     receiver</li>
	 *                                     </ul>
	 *
	 * @see Table#showSelection()
	 */
	public void showItem(TableItem item) {
		checkWidget();

		if (item == null) error(SWT.ERROR_NULL_ARGUMENT);
		if (item.isDisposed()) error(SWT.ERROR_INVALID_ARGUMENT);

		int index = indexOf(item);
		if (index != -1) {
			showItem(index);
		}
	}

	/**
	 * Shows the selection. If the selection is already showing in the receiver,
	 * this method simply returns. Otherwise, the items are scrolled until the
	 * selection is visible.
	 *
	 * @exception SWTException
	 *                         <ul>
	 *                         <li>ERROR_WIDGET_DISPOSED - if the receiver has been
	 *                         disposed</li>
	 *                         <li>ERROR_THREAD_INVALID_ACCESS - if not called from
	 *                         the thread that created the receiver</li>
	 *                         </ul>
	 *
	 * @see Table#showItem(TableItem)
	 */
	public void showSelection() {
		checkWidget();

		// TODO: check whether it is always the first selected element, which should be
		// visible.

		final int index = selectionModel.getSelectionIndex();
		if (index >= 0) {
			showItem(index);
		}
	}

	/* public */ void sort() {
		checkWidget();
		redraw();
	}

	void updateHeaderToolTips() {
		logNotImplemented();
	}

	void updateMoveable() {
		logNotImplemented();
	}

	static void logNotImplemented() {
		if (LOG_NOT_IMPLEMENTED) {
			System.out.println("WARN: Not implemented yet: " + new Throwable().getStackTrace()[1]);
		}
	}

	// TODO move this heuristic somewhere else.
	int guessTextHeight() {
		final GC gc = new GC(this);
		try {
			gc.setFont(getFont());
			return gc.getFontMetrics().getHeight();
		} finally {
			gc.dispose();
		}
	}

	Point computeTextExtent(String str) {
		final GC gc = new GC(this);
		try {
			return gc.textExtent(str, DRAW_FLAGS);
		} finally {
			gc.dispose();
		}
	}

	TableColumnsHandler getColumnsHandler() {
		return columnsHandler;
	}

	TableItemsHandler getItemsHandler() {
		return itemsHandler;
	}

	Point computeSize(TableColumn column) {
		return renderer.computeSize(column);
	}

	int guessColumnHeight(TableColumn column) {
		return renderer.guessColumnHeight(column);
	}

	Event sendMeasureItem(TableItem item, int column, GC gc, Rectangle bounds) {
		final Event event = new Event();
		event.widget = this;
		event.item = item;
		event.gc = gc;
		event.index = column;
		event.setBounds(bounds);
		if (hooks(SWT.MeasureItem)) {
			sendEvent(SWT.MeasureItem, event);
		}
		return event;
	}
}
