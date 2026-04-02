import { Component, Input, Output, EventEmitter, OnInit } from '@angular/core';
import { DxAutocompleteModule } from 'devextreme-angular';
import { TickDataService } from '../../services';

@Component({
  selector: 'app-tick-autocomplete',
  template: `
    <dx-autocomplete
      [dataSource]="symbols"
      placeholder="Type symbol..."
      [value]="value"
      (onValueChanged)="onValueChanged($event)"
      [showClearButton]="true">
    </dx-autocomplete>
  `,
  standalone: true,
  imports: [DxAutocompleteModule]
})
export class TickAutocomplete implements OnInit {
  @Input() value: string = '';
  @Output() valueChange = new EventEmitter<string>();

  symbols: string[] = [];

  constructor(private tickDataService: TickDataService) {}

  ngOnInit() {
    this.tickDataService.getSymbols().subscribe(data => {
      this.symbols = data;
    });
  }

  onValueChanged(e: any) {
    this.valueChange.emit(e.value);
  }
}
