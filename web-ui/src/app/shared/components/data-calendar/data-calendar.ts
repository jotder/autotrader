import { Component, Input, Output, EventEmitter, OnChanges, SimpleChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { DxCalendarModule } from 'devextreme-angular';

@Component({
  selector: 'app-data-calendar',
  template: `
    <dx-calendar
      selectionMode="multiple"
      [(value)]="value"
      (onValueChanged)="onValueChanged($event)"
      [cellTemplate]="'cellTemplate'">
      <div *dxTemplate="let cell of 'cellTemplate'" [class.available-data]="isDateAvailable(cell.date)">
        <span>{{ cell.text }}</span>
      </div>
    </dx-calendar>
  `,
  styles: [`
    .available-data {
      background-color: #d1e7dd;
      border-radius: 50%;
      font-weight: bold;
      color: #0f5132;
    }
  `],
  standalone: true,
  imports: [DxCalendarModule, CommonModule]
})
export class DataCalendar implements OnChanges {
  @Input() value: Date[] = [];
  @Input() availableDates: string[] = [];
  @Output() valueChange = new EventEmitter<Date[]>();

  ngOnChanges(changes: SimpleChanges) {
    if (changes['availableDates']) {
      // Refresh logic if needed
    }
  }

  onValueChanged(e: any) {
    this.valueChange.emit(e.value);
  }

  isDateAvailable(date: Date): boolean {
    if (!this.availableDates) return false;
    const dateString = this.formatDate(date);
    return this.availableDates.includes(dateString);
  }

  private formatDate(date: Date): string {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }
}
