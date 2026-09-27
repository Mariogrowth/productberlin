package net.productberlin.presentation.designsystem.layouts.tables

import react.FC
import react.Key
import react.Props
import react.dom.html.ReactHTML.caption
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.table
import react.dom.html.ReactHTML.tbody
import react.dom.html.ReactHTML.td
import react.dom.html.ReactHTML.th
import react.dom.html.ReactHTML.thead
import react.dom.html.ReactHTML.tr
import web.cssom.ClassName

external interface DenseTableProps : Props {
    var label: String
    var columns: List<TableColumn>
    var rows: List<TableRow>
}

val DenseTable =
    FC<DenseTableProps> { props ->
        require(props.rows.all { it.cells.size == props.columns.size }) { "Each row must have one cell per column" }
        div {
            className = ClassName("pb-table-scroll")
            tabIndex = 0
            ariaLabel = props.label
            table {
                className = ClassName("pb-table")
                caption { +props.label }
                thead {
                    tr {
                        props.columns.forEach { column ->
                            th {
                                className = if (column.numeric) ClassName("pb-numeric") else null
                                +column.label
                            }
                        }
                    }
                }
                tbody {
                    props.rows.forEach { row ->
                        tr {
                            key = Key(row.id)
                            row.cells.forEachIndexed { index, cell ->
                                td {
                                    className = if (props.columns[index].numeric) ClassName("pb-numeric") else null
                                    +cell
                                }
                            }
                        }
                    }
                }
            }
        }
    }
