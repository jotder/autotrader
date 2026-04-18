import { Component, Input} from '@angular/core';
import { CommonModule } from '@angular/common';
import { DxScrollViewModule }  from 'devextreme-angular/ui/scroll-view';

@Component({
  selector: 'app-single-card',
  templateUrl: './single-card.html',
  styleUrls: ['./single-card.scss'],
  standalone: true,
  imports: [ CommonModule, DxScrollViewModule ],
})
export class SingleCard {
  @Input()
  title!: string;

  @Input()
  description!: string;

  constructor() { }
}
